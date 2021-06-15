# jepsen.crdtcart

A Clojure library designed to test a [CRDT implementation of shopping cart](https://github.com/nikitakalyanov/crdt-cart) with Jepsen framework.

## Usage

1. Install Leiningen
2. Set up jepsen nodes. For example use LXC and instructions from Jepsen repo (https://github.com/jepsen-io/jepsen/blob/main/doc/lxc.md). Ubuntu-specific instructions (LXC-based nodes on Debian Buster) are in lxc-notes.md file.
3. Install Gnuplot
```
apt install gnuplot
```
4. Run like this (from this directory):
```
lein run test -n n1 --ssh-private-key ~/jepsen-repo/jepsen/docker/test-key.pem
```
## License

Copyright © 2021 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
