Everyone's heard the term, but what exactly are containers?

The software enabling this technology comes in many forms, with Docker as the most popular. The recent rise in popularity of container technology within the data center is a direct result of its portability and ability to isolate working environments, thus limiting its impact and overall footprint to the underlying computing system. To understand the technology completely, you first need to understand the many pieces that make it all possible.

Sidenote: people often ask about the difference between containers and virtual machines. Both have a specific purpose and place with very little overlap, and one doesn't obsolete the other. A container is meant to be a lightweight environment that you spin up to host one to a few isolated applications at bare-metal performance. You should opt for virtual machines when you want to host an entire operating system or ecosystem or maybe to run applications incompatible with the underlying environment.

Linux Control Groups
Truth be told, certain software applications in the wild may need to be controlled or limited—at least for the sake of stability and, to some degree, security. Far too often, a bug or just bad code can disrupt an entire machine and potentially cripple an entire ecosystem. Fortunately, a way exists to keep those same applications in check. Control groups (cgroups) is a kernel feature that limits, accounts for and isolates the CPU, memory, disk I/O and network's usage of one or more processes.

Originally developed by Google, the cgroups technology eventually would find its way to the Linux kernel mainline in version 2.6.24 (January 2008). A redesign of this technology—that is, the addition of kernfs (to split some of the sysfs logic)—would be merged into both the 3.15 and 3.16 kernels.

The primary design goal for cgroups was to provide a unified interface to manage processes or whole operating-system-level virtualization, including Linux Containers, or LXC (a topic I plan to revisit in more detail in a follow-up article). The cgroups framework provides the following:

Resource limiting: a group can be configured not to exceed a specified memory limit or use more than the desired amount of processors or be limited to specific peripheral devices.
Prioritization: one or more groups may be configured to utilize fewer or more CPUs or disk I/O throughput.
Accounting: a group's resource usage is monitored and measured.
Control: groups of processes can be frozen or stopped and restarted.
A cgroup can consist of one or more processes that are all bound to the same set of limits. These groups also can be hierarchical, which means that a subgroup inherits the limits administered to its parent group.

The Linux kernel provides access to a series of controllers or subsystems for the cgroup technology. The controller is responsible for distributing a specific type of system resources to a set of one or more processes. For instance, the memory controller is what limits memory usage while the cpuacct controller monitors CPU usage.

You can access and manage cgroups both directly and indirectly (with LXC, libvirt or Docker), the first of which I cover here via sysfs and the libcgroups library. To follow along with the examples here, you first need to install the necessary packages. On Red Hat Enterprise Linux or CentOS, type the following on the command line:

```bash
yum install libcgroup libcgroup-tools
```
On Ubuntu or Debian, type:

```bash
apt install libcgroup1 cgroup-tools
```

For the example application, I'm using a simple shell script file called test.sh, and it'll be running the following two commands in an infinite `while` loop:

```bash
$ cat test.sh
#!/bin/sh

while [ 1 ]; do
        echo "hello world"
        sleep 60
done
```

- The Manual Approach

With the proper packages installed, you can configure your cgroups directly via the sysfs hierarchy. For instance, to create a cgroup named `foo` under the `memory` subsystem, create a directory named foo in /sys/fs/cgroup/memory:

```bash
mkdir /sys/fs/cgroup/memory/foo
```

By default, every newly created cgroup will inherit access to the system's entire pool of memory. For some applications, primarily those that continue to allocate more memory but refuse to free what already has been allocated, that may not be such a great idea. To limit an application to a reasonable limit, you'll need to update the `memory.limit_in_bytes` file.

Limit the memory for anything running under the cgroup `foo` to 50MB:

```bash
echo 50000000 | sudo tee
↪/sys/fs/cgroup/memory/foo/memory.limit_in_bytes
```
 
 Verify the setting:
 
 ```bash
sudo cat memory.limit_in_bytes
50003968
```

Note that the value read back always will be a multiple of the kernel's page size (that is, 4096 bytes or 4KB). This value is the smallest allocatable size of memory.

Launch the application:

```bash
sh ~/test.sh &
```
Using its Process ID (PID), move the application to cgroup `foo` under the `memory` controller:


```bash
echo 2845 > /sys/fs/cgroup/memory/foo/cgroup.procs
```

Using the same PID number, list the running process and verify that it's running within the desired cgroup:

```bash
ps -o cgroup 2845
CGROUP
8:memory:/foo,1:name=systemd:/user.slice/user-0.slice/
↪session-4.scope
```

You also can monitor what's currently being used by that cgroup by reading the desired files. In this case, you'll want to see the amount of memory allocated by your process (and spawned subprocesses):


```bash
cat /sys/fs/cgroup/memory/foo/memory.usage_in_bytes
253952
```


- When a Process Goes Astray

Now let's re-create the same scenario, but instead of limiting the cgroup foo to 50MB of memory, you'll limit it to 500 bytes:


```bash
echo 500 | sudo tee /sys/fs/cgroup/memory/foo/
↪memory.limit_in_bytes
```

**Note**: if a task exceeds its defined limits, the kernel will intervene and, in some cases, kill that task.

Again, when you read the value back, it always will be a multiple of the kernel page size. So, although you set it to 500 bytes, it's really set to 4 KB:

```bash
cat /sys/fs/cgroup/memory/foo/memory.limit_in_bytes
4096
```


Launch the application, move it into the cgroup and monitor the system logs:


```bash
sudo tail -f /var/log/messages
Oct 14 10:22:40 localhost kernel: sh invoked oom-killer:
 ↪gfp_mask=0xd0, order=0, oom_score_adj=0
Oct 14 10:22:40 localhost kernel: sh cpuset=/ mems_allowed=0
Oct 14 10:22:40 localhost kernel: CPU: 0 PID: 2687 Comm:
 ↪sh Tainted: G
OE  ------------   3.10.0-327.36.3.el7.x86_64 #1
Oct 14 10:22:40 localhost kernel: Hardware name: innotek GmbH
VirtualBox/VirtualBox, BIOS VirtualBox 12/01/2006
Oct 14 10:22:40 localhost kernel: ffff880036ea5c00
 ↪0000000093314010 ffff88000002bcd0 ffffffff81636431
Oct 14 10:22:40 localhost kernel: ffff88000002bd60
 ↪ffffffff816313cc 01018800000000d0 ffff88000002bd68
Oct 14 10:22:40 localhost kernel: ffffffffbc35e040
 ↪fffeefff00000000 0000000000000001 ffff880036ea6103
Oct 14 10:22:40 localhost kernel: Call Trace:
Oct 14 10:22:40 localhost kernel: [<ffffffff81636431>]
 ↪dump_stack+0x19/0x1b
Oct 14 10:22:40 localhost kernel: [<ffffffff816313cc>]
 ↪dump_header+0x8e/0x214
Oct 14 10:22:40 localhost kernel: [<ffffffff8116d21e>]
 ↪oom_kill_process+0x24e/0x3b0
Oct 14 10:22:40 localhost kernel: [<ffffffff81088e4e>] ?
 ↪has_capability_noaudit+0x1e/0x30
Oct 14 10:22:40 localhost kernel: [<ffffffff811d4285>]
 ↪mem_cgroup_oom_synchronize+0x575/0x5a0
Oct 14 10:22:40 localhost kernel: [<ffffffff811d3650>] ?
 ↪mem_cgroup_charge_common+0xc0/0xc0
Oct 14 10:22:40 localhost kernel: [<ffffffff8116da94>]
 ↪pagefault_out_of_memory+0x14/0x90
Oct 14 10:22:40 localhost kernel: [<ffffffff8162f815>]
 ↪mm_fault_error+0x68/0x12b
Oct 14 10:22:40 localhost kernel: [<ffffffff816422d2>]
 ↪__do_page_fault+0x3e2/0x450
Oct 14 10:22:40 localhost kernel: [<ffffffff81642363>]
 ↪do_page_fault+0x23/0x80
Oct 14 10:22:40 localhost kernel: [<ffffffff8163e648>]
 ↪page_fault+0x28/0x30
Oct 14 10:22:40 localhost kernel: Task in /foo killed as
 ↪a result of limit of /foo
Oct 14 10:22:40 localhost kernel: memory: usage 4kB, limit
 ↪4kB, failcnt 8
Oct 14 10:22:40 localhost kernel: memory+swap: usage 4kB,
 ↪limit 9007199254740991kB, failcnt 0
Oct 14 10:22:40 localhost kernel: kmem: usage 0kB, limit
 ↪9007199254740991kB, failcnt 0
Oct 14 10:22:40 localhost kernel: Memory cgroup stats for /foo:
 ↪cache:0KB rss:4KB rss_huge:0KB mapped_file:0KB swap:0KB
 ↪inactive_anon:0KB active_anon:0KB inactive_file:0KB
 ↪active_file:0KB unevictable:0KB
Oct 14 10:22:40 localhost kernel: [ pid ]   uid  tgid total_vm
 ↪rss nr_ptes swapents oom_score_adj name
Oct 14 10:22:40 localhost kernel: [ 2687]     0  2687    28281
 ↪347     12        0             0 sh
Oct 14 10:22:40 localhost kernel: [ 2702]     0  2702    28281
 ↪50    7        0             0 sh
Oct 14 10:22:40 localhost kernel: Memory cgroup out of memory:
 ↪Kill process 2687 (sh) score 0 or sacrifice child
Oct 14 10:22:40 localhost kernel: Killed process 2702 (sh)
 ↪total-vm:113124kB, anon-rss:200kB, file-rss:0kB
Oct 14 10:22:41 localhost kernel: sh invoked oom-killer:
 ↪gfp_mask=0xd0, order=0, oom_score_adj=0
[ ... ]
```

Notice that the kernel's Out-Of-Memory Killer (or oom-killer) stepped in as soon as the application hit that 4KB limit. It killed the application, and it's no longer running. You can verify this by typing:

```bash
ps -o cgroup 2687
CGROUP
```

- Using libcgroup

Many of the earlier steps described here are simplified by the administration utilities provided in the `libcgroup` package. For example, a single command invocation using the `cgcreate` binary takes care of the process of creating the sysfs entries and files.
To create the group named `foo` under the `memory` subsystem, type the following:


```bash
sudo cgcreate -g memory:foo
```


Note: libcgroup provides a mechanism for managing tasks in control groups.

Using the same methods as before, you can begin to set thresholds:

```bash
echo 50000000 | sudo tee
 ↪/sys/fs/cgroup/memory/foo/memory.limit_in_bytes
```

Verify the newly configured setting:


```bash
sudo cat memory.limit_in_bytes
50003968
```

Run the application in the cgroup `foo` using the `cgexec` binary:

```bash
sudo cgexec -g memory:foo ~/test.sh
```


Using its PID number, verify that the application is running in the cgroup and under defined subsystem (`memory`):


```bash
ps -o cgroup 2945
CGROUP
6:memory:/foo,1:name=systemd:/user.slice/user-0.slice/
↪session-1.scope
```


If your application is no longer running and you want to clean up and remove the cgroup, you would do that by using the `cgdelete` binary. To remove group `foo` from under the `memory` controller, type:

```bash
sudo cgdelete memory:foo
```


- Persistent Groups

You also can accomplish all of the above from a simple configuration file and the starting of a service. You can define all of your cgroup names and attributes in the /etc/cgconfig.conf file. The following appends a few attributes for the group `foo`:


```bash
$ cat /etc/cgconfig.conf
#
#  Copyright IBM Corporation. 2007
#
#  Authors:     Balbir Singh <balbir@linux.vnet.ibm.com>
#  This program is free software; you can redistribute it
#  and/or modify it under the terms of version 2.1 of the GNU
#  Lesser General Public License as published by the Free
#  Software Foundation.
#
#  This program is distributed in the hope that it would be
#  useful, but WITHOUT ANY WARRANTY; without even the implied
#  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
#  PURPOSE.
#
#
# By default, we expect systemd mounts everything on boot,
# so there is not much to do.
# See man cgconfig.conf for further details, how to create
# groups on system boot using this file.

group foo {
  cpu {
    cpu.shares = 100;
  }
  memory {
    memory.limit_in_bytes = 5000000;
  }
}
```

The `cpu.shares` options defines the CPU priority of the group. By default, all groups inherit 1,024 shares or 100% of CPU time. By bringing this value down to something a bit more conservative, like 100, the group will be limited to approximately 10% of the CPU time.

As discussed earlier, a process running within a cgroup also can be limited to the amount of CPUs (cores) it can access. Add the following section to the same cgconfig.conf file and under the desired group name:

```bash
cpuset {
  cpuset.cpus="0-5";
}
```

With this limit, this cgroup will bind the application to cores 0 to 5—that is, it will see only the first six CPU cores on the system.

Next, you need to load this configuration using the `cgconfig` service. First, enable `cgconfig` to load the above configuration on system boot up:


```bash
sudo systemctl enable cgconfig
Create symlink from /etc/systemd/system/sysinit.target.wants/
↪cgconfig.service
to /usr/lib/systemd/system/cgconfig.service.
```

Now, start the `cgconfig`service and load the same configuration file manually (or you can skip this step and reboot the system):


```bash
sudo systemctl start cgconfig
```


Launch the application into the cgroup `foo` and bind it to your `memory` and `cpu` limits:


```bash
sudo cgexec -g memory,cpu,cpuset:foo ~/test.sh &
```


With the exception of launching the application into the predefined cgroup, all the rest will persist across system reboots. However, you can automate that process by defining a startup init script dependent on the `cgconfig` service to launch that same application.

- Summary
Often it becomes necessary to limit one or more tasks on a machine. Control groups provide that functionality, and by leveraging it, you can enforce strict hardware and software limitations to some of your most vital or uncontrollable applications. If one application does not set an upper threshold or limit the amount of memory it can consume on a system, cgroups can address that. If another application tends to be a bit of a CPU hog, again, cgroups has got you covered. You can accomplish so much with cgroups, and with a little time invested, you'll restore stability, security and sanity back into your operating environment.

In Part II of this series, I move beyond Linux control groups and shift focus to how technologies like Linux Containers make use of them.
