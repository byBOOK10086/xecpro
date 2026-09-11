// SPDX-License-Identifier: GPL-2.0-or-later

/*
 * Legacy KernelSU prctl() supercall compatibility layer.
 *
 * Upstream KernelSU historically exposed a prctl(0xdeadbeef, CMD, ...) interface
 * that userspace clients (notably Zygisk Next) use to detect the root
 * implementation and to query the per-UID exclude list. This fork moved to the
 * ioctl()-based supercall and stopped hooking __NR_prctl, so Zygisk Next reports
 * "无法确定 root 实现" and its exclude list silently fails.
 *
 * This file restores that legacy ABI on top of the existing syscall dispatcher.
 */

#include <asm/syscall.h>
#include <linux/compiler.h>
#include <linux/cred.h>
#include <linux/printk.h>
#include <linux/ptrace.h>
#include <linux/types.h>
#include <linux/uaccess.h>

#include "arch.h"
#include "klog.h" // IWYU pragma: keep
#include "hook/syscall_hook.h"
#include "manager/manager_identity.h"
#include "policy/allowlist.h"

#define KERNEL_SU_OPTION 0xdeadbeef

#define CMD_GET_VERSION 2
#define CMD_UID_GRANTED_ROOT 12
#define CMD_UID_SHOULD_UMOUNT 13

/*
 * Zygisk Next accepts KernelSU versions in [10940, 20000]; anything above is
 * classified as "Abnormal" and every feature (denylist included) is disabled.
 * This fork's KERNEL_SU_VERSION is >= 30000, so report a clamped value that
 * stays firmly inside the supported window.
 */
#define PRCTL_COMPAT_KSU_VERSION 20000

static long ksu_handle_prctl(unsigned long arg2, unsigned long arg3,
			     unsigned long arg4, unsigned long arg5)
{
	u32 *result = (u32 *)arg5;
	u32 reply_ok = KERNEL_SU_OPTION;
	/* Success in this interface is signalled by writing the magic to arg5. */

	switch (arg2) {
	case CMD_GET_VERSION: {
		u32 version = PRCTL_COMPAT_KSU_VERSION;
		if (copy_to_user((void __user *)arg3, &version,
				 sizeof(version))) {
			pr_err("prctl compat: GET_VERSION copy err\n");
			return -EFAULT;
		}
		if (arg4) {
			u32 version_flags = 0;
			if (copy_to_user((void __user *)arg4, &version_flags,
					 sizeof(version_flags))) {
				pr_err("prctl compat: GET_VERSION flags copy err\n");
				return -EFAULT;
			}
		}
		return 0;
	}
	case CMD_UID_GRANTED_ROOT:
	case CMD_UID_SHOULD_UMOUNT: {
		uid_t target_uid = (uid_t)arg3;
		bool allow = false;
		if (arg2 == CMD_UID_GRANTED_ROOT) {
			allow = ksu_is_allow_uid(target_uid);
		} else {
			allow = ksu_uid_should_umount(target_uid);
		}
		if (copy_to_user((void __user *)arg4, &allow, sizeof(allow))) {
			pr_err("prctl compat: allow copy err\n");
			return -EFAULT;
		}
		if (copy_to_user(result, &reply_ok, sizeof(reply_ok))) {
			pr_err("prctl compat: reply copy err\n");
			return -EFAULT;
		}
		return 0;
	}
	default:
		/* Unrecognized legacy command: swallow it, as upstream did. */
		return 0;
	}
}

long __nocfi ksu_hook_prctl(int orig_nr, const struct pt_regs *regs)
{
	int option = (int)PT_REGS_PARM1(regs);
	unsigned long arg2;
	unsigned long arg3;
	unsigned long arg4;
	unsigned long arg5;
	bool from_root;
	bool from_manager;

	/* Not our magic: hand it to the real prctl syscall. */
	if (option != KERNEL_SU_OPTION) {
		return ksu_syscall_table[orig_nr](regs);
	}

	arg2 = (unsigned long)PT_REGS_PARM2(regs);
	arg3 = (unsigned long)PT_REGS_PARM3(regs);
	arg4 = (unsigned long)PT_REGS_SYSCALL_PARM4(regs);
	arg5 = (unsigned long)PT_REGS_PARM5(regs);

	/* Only root or the manager may talk to the supercall interface. */
	from_root = (0 == current_uid().val);
	from_manager = is_manager();
	if (!from_root && !from_manager) {
		return 0;
	}

	return ksu_handle_prctl(arg2, arg3, arg4, arg5);
}