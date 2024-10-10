# Distance-Vector-Routing
Building a routing table using distance vector routing

## Overview

Create a virtual router to build a routing table using distance vector routing.
With this change, the virtual router will no longer depend on a static route table.


## 1. Getting Started

TCPdump is a command-line packet analyzer tool used for capturing and analyzing network packets. It
allows users to intercept and display the packets transmitted over a network interface in real-time or to
save them to a file for later analysis. TCPdump can capture and analyze packets at the packet level,
providing insights into network traffic, protocols being used, and potential issues such as network congestion
or security threats. It is widely used by network administrators, security professionals, and developers for
troubleshooting network issues, monitoring network activity, and analyzing network traffic patterns. As you
complete this assignment, you may want to usetcpdumpto examine the headers of packets sent/received by
hosts. This will help you identify potential issues in your implementation. To record network traffic with
tcpdumpon a specific host, open an xterm window:

```bash
mininet> xterm h
```

Then start tcpdump in that xterm:

```bash
sudo tcpdump -n -vv -e -i h1-eth
```

This will cause tcpdump to print brief messages on the packets sent/received on the interfaceh1-eth0.
You’ll need to change the host number included in the interface (-i) argument to match the host on which
you’re running tcpdump. tcpdump is also capable of storing the packet contents into a capture dump for
offline analysis with-w. Consult the manual of tcpdump (withman tcpdump) for advanced usage. Come to
office hours if you have issue debugging withtcpdump.


## 2. RIP

I implemented distance vector routing to build, and update, the router’s
route table. Specifically, I implemented a simplified version of the Routing Information Protocol v
(RIPv2). Details on the RIPv2 protocol are available from RFC2453 and Network Sorcery RFC Sourcebook.

### Starting RIP

The router only runs RIP when a static route table is not provided (via the-rargument when
running VirtualNetwork.jar).

When the router starts, it adds entries to the route table for the subnets that are directly reachable
via the router’s interfaces. This subnets can be determined based on the IP address and netmask associated
with each of the router’s interfaces. These entries should have no gateway.

### RIP Packets

The RIPv2 and RIPv2Entry classes in the net.floodlightcontroller.packet package define the format
for RIPv2 packets. All RIPv2 packets should be encapsulated in UDP packets whose source and destination
ports are 520 (defined as a constant RIPPORT in the UDP class). When sending RIP requests and unsolicited
RIP responses, the destination IP address should be the multicast IP address reserved for RIP (224.0.0.9)
and the destination Ethernet address should be the broadcast MAC address (FF:FF:FF:FF:FF:FF). When
sending a RIP response for a specific RIP request, the destination IP address and destination Ethernet
address should be the IP address and MAC address of the router interface that sent the request.

### RIP Operation

The router sends a RIP request out all of the router’s interfaces when RIP is initialized. The router sends
an unsolicited RIP response out all of the router’s interfaces every 10 seconds thereafter.

The handlePacket(...) method in theRouter class checks if an arriving IP packet is
of protocol type UDP, and a UDP destination port of 520. Packets that match this criteria are RIP requests
or responses. Your router should update its route table based on these packets, and send any necessary RIP
response packets.

The router times out route table entries for which an update has not been received for more than 30
seconds. It never removes route entries for the subnets that are directly reachable via the router’s
interfaces.

The implementation is not a complete standards-compliant implementation of RIPv2.

### Testing RIP

To test the router’s control plane, you will need a topology with more than one router: pairrt.topo,
trianglert.topo, trianglewithsw.topo, or linear5.topo. Do not include the -r argument
when starting the routers, since the router should construct its route table using RIP, rather than using
a statically provided route table. 

```bash
java -jar VirtualNetwork.jar -v r1 -a arp_cache
```