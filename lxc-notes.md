Some links:
https://github.com/jepsen-io/jepsen/blob/main/doc/lxc.md
https://ubuntu.com/server/docs/containers-lxc
(but this is for server ubuntu, not desktop)
Setting up LXC nodes on Ubuntu is mostly from these instructions, but:
1. Some problems with DNS, manual /etc/hosts intervention was needed
2. LXC expects older ssh private key format, convert existing keys if needed
3. -t debian is not working, but -t download --dist debian --release buster --arch amd64 does
4. Most LXC management requires root
5. There were no preinstalled openssh-server in LXC container, installation is needed
6. Managing same configuration in multiple nodes could quickly get hard.


# LXC Node setup
```bash
for i in {1..5}; do
  sudo lxc-start -d -n n$i
done

# cp will not overwrite files, if you already have something in dest dir
# don't forget to rm it first
for i in {1..5}; do
  sudo cp -r ~/crdt-cart/ /var/lib/lxc/n${i}/rootfs/crdt-cart/
done

for i in {1..5}; do
  sudo lxc-attach -n n${i} -- apt install -y openssh-server python3 python3-pip libpq-dev iptables sudo
  sudo lxc-attach -n n${i} -- pip3 install psycopg2
  sudo lxc-attach -n n${i} -- bash -c 'cd /crdt-cart/; pip3 install -r requirements.txt';
done
```

Add this to your /etc/hosts
```
10.0.3.80 n1
10.0.3.72 n2
10.0.3.160 n3
10.0.3.217 n4
10.0.3.4 n5
```
you can take container IPs from sudo lxc-info n1, sudo lxc-info n2, etc.

```bash
# allow root login with password root
for i in {1..5}; do
  sudo lxc-attach -n n${i} -- bash -c 'echo -e "root\nroot\n" | passwd root';
  sudo lxc-attach -n n${i} -- sed -i 's,^#\?PermitRootLogin .*,PermitRootLogin yes,g' /etc/ssh/sshd_config;
  sudo lxc-attach -n n${i} -- systemctl restart sshd;
done

# ssh to node like this
sudo ssh -i /home/nkalyanov/jepsen-repo/jepsen/docker/test-key.pem root@n1
# client should listen 0.0.0.0 so that jepsen can connect
```

enter each of the containers and run
```bash
DB_HOST=172.17.0.2 python3 crdt-cart/http_server.py
```
use docker container ip address to connect to postgesql (docker inspect)

```bash
SERVER_HOST=172.17.0.3 python3 crdt-cart/tcp_serving_client.py
```
use load balancer docker container ip address
