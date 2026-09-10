use std::{
    ffi::OsStr,
    fs,
    os::unix::fs::PermissionsExt,
    path::Path,
    process::Command,
};

use anyhow::{Context, Result, bail};

const KPM_DIR: &str = "/data/adb/kpm";
// The core is staged on demand into tmpfs (never under /data/adb), so a
// freshly-rooted device has no extra files until the user drops modules in.
const KPM_CORE_BIN: &str = "/dev/.kpmd";

/// Run the on-demand module core and return its stdout.
fn run_kpm_core(args: &[&str]) -> Result<String> {
    ensure_kpm_core()?;
    let out = Command::new(KPM_CORE_BIN)
        .args(args)
        .output()
        .with_context(|| format!("failed to run core {args:?}"))?;
    Ok(String::from_utf8_lossy(&out.stdout).into_owned())
}

/// Decode the bundled core (obfuscated at rest) before staging it.
fn decode_core(data: &[u8]) -> Vec<u8> {
    const KEY: &[u8] = b"xdcv1";
    data.iter()
        .enumerate()
        .map(|(i, b)| b ^ KEY[i % KEY.len()])
        .collect()
}

/// Stage the bundled core into tmpfs if it is not already there.
fn ensure_kpm_core() -> Result<()> {
    let bin = Path::new(KPM_CORE_BIN);
    if bin.exists() {
        return Ok(());
    }
    let asset = crate::assets::get_asset("kpmd").with_context(|| "core asset not bundled")?;
    let decoded = decode_core(asset.as_ref().as_ref());
    fs::write(bin, decoded).with_context(|| format!("failed to write {}", bin.display()))?;
    fs::set_permissions(bin, fs::Permissions::from_mode(0o755))?;
    Ok(())
}

/// Remove the staged core so nothing lingers after use.
fn drop_kpm_core() {
    let _ = fs::remove_file(KPM_CORE_BIN);
}

/// True when the user actually placed at least one module to load.
fn has_modules() -> bool {
    let Ok(dir) = fs::read_dir(KPM_DIR) else {
        return false;
    };
    dir.flatten()
        .any(|e| e.path().extension() == Some(OsStr::new("kpm")))
}

pub fn load_module<P>(path: P, args: Option<&str>) -> Result<()>
where
    P: AsRef<Path>,
{
    let path = path.as_ref().to_string_lossy();
    let mut argv: Vec<&str> = vec!["kpm", "load", path.as_ref()];
    if let Some(a) = args {
        if !a.is_empty() {
            argv.push(a);
        }
    }
    let out = run_kpm_core(&argv)?;
    let out = out.trim();
    if !out.is_empty() {
        println!("{out}");
    }
    Ok(())
}

pub fn list() -> Result<()> {
    let out = run_kpm_core(&["kpm", "list"])?;
    print!("{out}");
    Ok(())
}

pub fn unload_module(name: String) -> Result<()> {
    let out = run_kpm_core(&["kpm", "unload", &name])?;
    let out = out.trim();
    if !out.is_empty() {
        println!("{out}");
    }
    Ok(())
}

pub fn info(name: String) -> Result<()> {
    let out = run_kpm_core(&["kpm", "info", &name])?;
    print!("{out}");
    Ok(())
}

pub fn control(name: String, args: String) -> Result<i32> {
    let out = run_kpm_core(&["kpm", "ctl0", &name, &args])?;
    let out = out.trim();
    if !out.is_empty() {
        println!("{out}");
    }
    Ok(0)
}

pub fn num() -> Result<i32> {
    let out = run_kpm_core(&["kpm", "num"])?;
    let n = out.trim().parse::<i32>().unwrap_or(0);
    println!("{n}");
    Ok(n)
}

pub fn version() -> Result<()> {
    let out = run_kpm_core(&["kpver"])?;
    print!("{out}");
    Ok(())
}

pub fn check_version() -> Result<String> {
    let out = run_kpm_core(&["hello"])?;
    let out = out.trim();
    if out.is_empty() {
        bail!("KPM: core not ready (hello returned empty)");
    }
    log::info!("KPM: core ok: {out}");
    Ok(out.to_string())
}

fn ensure_dir() -> Result<()> {
    let dir = Path::new(KPM_DIR);

    if !dir.exists() {
        let _ = fs::create_dir_all(KPM_DIR);
    }

    if dir.metadata()?.permissions().mode() != 0o777 {
        fs::set_permissions(KPM_DIR, fs::Permissions::from_mode(0o777))?;
    }

    Ok(())
}

pub fn booted_load() -> Result<()> {
    // Stage nothing unless the user actually placed modules; this keeps
    // /data/adb (and /dev) clean before any modules are flashed.
    if !has_modules() {
        return Ok(());
    }

    ensure_kpm_core()?;

    let hello = run_kpm_core(&["hello"]).unwrap_or_default();
    if hello.trim().is_empty() {
        drop_kpm_core();
        log::info!("KPM: core not ready, skip");
        return Ok(());
    }

    ensure_dir()?;

    if crate::utils::is_safe_mode() {
        log::warn!("KPM: safe-mode, skip");
        drop_kpm_core();
        return Ok(());
    }

    load_all_modules()?;
    drop_kpm_core();

    Ok(())
}

fn load_all_modules() -> Result<()> {
    let dir = Path::new(KPM_DIR);

    if !dir.is_dir() {
        return Ok(());
    }

    for entry in dir.read_dir()? {
        let p = entry?.path();

        if let Some(ex) = p.extension()
            && ex == OsStr::new("kpm")
        {
            load_module(p, None)?;
        }
    }
    Ok(())
}
