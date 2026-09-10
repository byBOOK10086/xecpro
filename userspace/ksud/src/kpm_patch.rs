// SPDX-License-Identifier: GPL-2.0-only

use std::fs;
use std::io::{Cursor, Write};
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};
use std::process::Command;

use android_bootimg::parser::BootImage;
use android_bootimg::patcher::BootImagePatchOption;
use anyhow::{Context, Result, anyhow, bail, ensure};

use crate::assets;

// tmpfs staging paths (never under /data/adb).
const PATCH_TOOL_BIN: &str = "/dev/.kptd";
const PATCH_CORE_BIN: &str = "/dev/.kpim";
const KERNEL_IN: &str = "/dev/.kkin";
const KERNEL_OUT: &str = "/dev/.kkot";

const XOR_KEY: &[u8] = b"xdcv1";

fn decode(data: &[u8]) -> Vec<u8> {
    data.iter()
        .enumerate()
        .map(|(i, b)| b ^ XOR_KEY[i % XOR_KEY.len()])
        .collect()
}

/// Stage a bundled (obfuscated) asset into tmpfs.
fn stage_asset(name: &str, dst: &str) -> Result<()> {
    if Path::new(dst).exists() {
        return Ok(());
    }
    let asset = assets::get_asset(name).with_context(|| format!("asset {name} not bundled"))?;
    let decoded = decode(asset.as_ref().as_ref());
    fs::write(dst, decoded).with_context(|| format!("failed to write {dst}"))?;
    fs::set_permissions(dst, fs::Permissions::from_mode(0o755))?;
    Ok(())
}

/// Removes all tmpfs staging files on drop (success or failure).
struct CleanupGuard;
impl Drop for CleanupGuard {
    fn drop(&mut self) {
        for p in [PATCH_TOOL_BIN, PATCH_CORE_BIN, KERNEL_IN, KERNEL_OUT] {
            let _ = fs::remove_file(p);
        }
    }
}

#[derive(clap::Args, Debug)]
pub struct BootPatchKpmArgs {
    /// Source boot image (contains the kernel to patch)
    #[arg(short, long)]
    pub boot: PathBuf,

    /// Patched boot image output
    #[arg(short, long)]
    pub output: PathBuf,

    /// Replace an existing output file
    #[arg(long, default_value = "false")]
    pub force: bool,
}

pub fn patch_kpm(args: &BootPatchKpmArgs) -> Result<()> {
    ensure!(
        args.boot.is_file(),
        "boot image does not exist: {}",
        args.boot.display()
    );
    if args.output.exists() {
        ensure!(
            args.force,
            "output already exists: {}; use --force",
            args.output.display()
        );
    }

    let _guard = CleanupGuard;

    println!("- Reading boot image");
    let boot_data = fs::read(&args.boot)
        .with_context(|| format!("cannot read boot image {}", args.boot.display()))?;
    let boot_image = BootImage::parse(&boot_data).context("cannot parse boot image")?;
    let kernel = boot_image
        .get_blocks()
        .get_kernel()
        .context("boot image does not contain a kernel")?;

    println!("- Decompressing kernel");
    let mut raw_kernel = Vec::new();
    kernel
        .dump(&mut raw_kernel, false)
        .context("cannot decompress boot kernel")?;

    stage_asset("kptd", PATCH_TOOL_BIN)?;
    stage_asset("kpim", PATCH_CORE_BIN)?;
    fs::write(KERNEL_IN, &raw_kernel).context("cannot stage kernel")?;

    println!("- Patching kernel");
    let status = Command::new(PATCH_TOOL_BIN)
        .args(["-p", "-i", KERNEL_IN, "-k", PATCH_CORE_BIN, "-o", KERNEL_OUT])
        .output()
        .context("cannot run patch tool")?;
    if !status.status.success() {
        bail!(
            "patch tool failed: {}",
            String::from_utf8_lossy(&status.stderr).trim()
        );
    }

    let patched_kernel = fs::read(KERNEL_OUT).context("cannot read patched kernel")?;

    println!("- Repacking boot image");
    let mut patcher = BootImagePatchOption::new(&boot_image);
    patcher.replace_kernel(Box::new(Cursor::new(patched_kernel)), false);
    let mut repacked = Cursor::new(Vec::with_capacity(boot_data.len()));
    patcher
        .patch(&mut repacked)
        .context("cannot repack boot image")?;
    let repacked = repacked.into_inner();

    let output_parent = args
        .output
        .parent()
        .filter(|path| !path.as_os_str().is_empty())
        .unwrap_or_else(|| Path::new("."));
    fs::create_dir_all(output_parent)
        .with_context(|| format!("cannot create output directory {}", output_parent.display()))?;
    let mut temporary = tempfile::NamedTempFile::new_in(output_parent)
        .context("cannot create temporary boot image")?;
    temporary
        .write_all(&repacked)
        .context("cannot write patched boot image")?;
    temporary
        .flush()
        .context("cannot flush patched boot image")?;
    let mode = fs::metadata(&args.boot)
        .with_context(|| format!("cannot stat boot image {}", args.boot.display()))?
        .permissions()
        .mode();
    temporary
        .as_file()
        .set_permissions(fs::Permissions::from_mode(mode))
        .context("cannot copy boot image permissions")?;
    temporary.persist(&args.output).map_err(|error| {
        anyhow!(
            "cannot persist patched boot image {}: {}",
            args.output.display(),
            error.error
        )
    })?;

    println!("- Output: {}", args.output.display());
    Ok(())
}
