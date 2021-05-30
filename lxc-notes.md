Setting up LXC nodes on Ubuntu is mostly from these instructions, but:
1. Some problems with DNS, manual /etc/hosts intervention was needed
2. LXC expects older ssh private key format, convert existing keys if needed
3. -t debian is not working, but -t download --dist debian --release buster --arch amd64 does
4. Most LXC management requires root
5. There were no preinstalled openssh-server in LXC container, installation is needed
6. Managing same configuration in multiple nodes could quickly get hard.
