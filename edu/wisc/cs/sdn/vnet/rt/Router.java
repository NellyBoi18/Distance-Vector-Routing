package edu.wisc.cs.sdn.vnet.rt;

import java.util.ArrayList;
import java.util.Map;
import java.util.TimerTask;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.IPv4;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.RIPv2Entry;
import net.floodlightcontroller.packet.MACAddress;


import java.util.Timer;
import java.util.TimerTask;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{
        private static final int RIP_PORT = 520;
        private static final String RIP_MULTICAST_IP = "224.0.0.9";
        private static final String BROADCAST_MAC = "FF:FF:FF:FF:FF:FF";
        
        /** Routing table for the router */
        private RouteTable routeTable;

        /** ARP cache for the router */
        private ArpCache arpCache;



        /**
         * Creates a router for a specific host.
         * @param host hostname for the router
         */
        public Router(String host, DumpFile logfile)
        {
                super(host,logfile);
                this.routeTable = new RouteTable();
                this.arpCache = new ArpCache();
                startRouteTimeoutChecker();

        }
        
        /**
         * @return routing table for the router
         */
        public RouteTable getRouteTable()
        { return this.routeTable; }

        /**
         * Load a new routing table from a file.
         * @param routeTableFile the name of the file containing the routing table
         */
        public void loadRouteTable(String routeTableFile)
        {
                if (!routeTable.load(routeTableFile, this))
                {
                        System.err.println("Error setting up routing table from file "
                                        + routeTableFile);
                        System.exit(1);
                }

                System.out.println("Loaded static route table");
                System.out.println("-------------------------------------------------");
                System.out.print(this.routeTable.toString());
                System.out.println("-------------------------------------------------");
        }

        /**
         * Load a new ARP cache from a file.
         * @param arpCacheFile the name of the file containing the ARP cache
         */
        public void loadArpCache(String arpCacheFile)
        {
                if (!arpCache.load(arpCacheFile))
                {
                        System.err.println("Error setting up ARP cache from file "
                                        + arpCacheFile);
                        System.exit(1);
                }

                System.out.println("Loaded static ARP cache");
                System.out.println("----------------------------------");
                System.out.print(this.arpCache.toString());
                System.out.println("----------------------------------");
        }

        

        /**
         * Handle an Ethernet packet received on a specific interface.
         * @param etherPacket the Ethernet packet that was received
         * @param inIface the interface on which the packet was received
         */
        @Override
        public void handlePacket(Ethernet etherPacket, Iface inIface) {
            if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
                return;
            }
        
            IPv4 ipPacket = (IPv4) etherPacket.getPayload();
            short originalChecksum = ipPacket.getChecksum();
            ipPacket.resetChecksum();
            byte[] serializedIP = ipPacket.serialize();
            ipPacket.deserialize(serializedIP, 0, serializedIP.length);
        
            if (originalChecksum != ipPacket.getChecksum()) {
                return; // Drop the packet if checksum verification fails
            }
        
            // Check if the packet is a UDP packet and destined for RIP port (520)
            if (ipPacket.getProtocol() == IPv4.PROTOCOL_UDP) {
                UDP udpPacket = (UDP) ipPacket.getPayload();
                if (udpPacket.getDestinationPort() == RIP_PORT) {
                    // Process RIP packet
                    processRipPacket(etherPacket, inIface);
                    return; // Exit the method after processing RIP packet
                }
            }

            if (0 == (ipPacket.getTtl() - 1)) {
                return; // Drop the packet if TTL is 0
            }
        
            ipPacket.setTtl((byte) (ipPacket.getTtl() - 1));
            ipPacket.resetChecksum();
            serializedIP = ipPacket.serialize();
            ipPacket.deserialize(serializedIP, 0, serializedIP.length);
        
            int destIpAddress = ipPacket.getDestinationAddress();
            RouteEntry bestMatch = this.routeTable.lookup(destIpAddress);
            if (bestMatch == null) {
                return; // No route to destination, drop the packet
            }
        
            int nextHopIp = bestMatch.getGatewayAddress();
            if (0 == nextHopIp) {
                nextHopIp = destIpAddress; // Directly connected network
            }
        
            ArpEntry arpEntry = this.arpCache.lookup(nextHopIp);
            if (arpEntry == null) {
                // Need to handle ARP lookup and possibly queue the packet for transmission when ARP reply is received
                return;
            }
        
            etherPacket.setSourceMACAddress(bestMatch.getInterface().getMacAddress().toBytes());
            etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());
            this.sendPacket(etherPacket, bestMatch.getInterface());
        }
        
        public void startRouteTimeoutChecker() {
            Timer timer = new Timer(true);
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    checkRouteTimeouts();
                }
            }, 0, 30000); // Check every 30 seconds
        }
        
        public void initializeRIP() {
            // Add directly connected routes to the routing table
            for (Iface iface : this.interfaces.values()) {
                int ip = iface.getIpAddress();
                int mask = iface.getSubnetMask();
                int networkAddress = ip & mask; // Compute the network address
        
                // Inserting the directly connected route into the routing table
                this.routeTable.insert(networkAddress, 0, mask, iface); // 0 for gateway IP in directly connected networks
            }
        
            // Send RIP request to discover neighbors
            sendRipRequest();
        
            // Start periodic unsolicited RIP responses
            Timer timer = new Timer(true);
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    sendRipResponse();
                }
            }, 10000, 10000); // every 10 seconds
        }
        


        public void sendRipRequest() {
            RIPv2 ripPacket = new RIPv2();
            ripPacket.setCommand(RIPv2.COMMAND_REQUEST);
            // For a request, entries are usually not needed
    
            UDP udpPacket = new UDP();
            udpPacket.setSourcePort((short) RIP_PORT);
            udpPacket.setDestinationPort((short) RIP_PORT);
            udpPacket.setPayload(ripPacket);

            IPv4 ipPacket = new IPv4();
            ipPacket.setTtl((byte) 1);
            ipPacket.setProtocol(IPv4.PROTOCOL_UDP);
            ipPacket.setSourceAddress("0.0.0.0"); // This can be router's IP if necessary
            ipPacket.setDestinationAddress(IPv4.toIPv4Address(RIP_MULTICAST_IP));
            ipPacket.setPayload(udpPacket);
    
            Ethernet etherPacket = new Ethernet();
            etherPacket.setEtherType(Ethernet.TYPE_IPv4);
            etherPacket.setSourceMACAddress(new byte[6]); // Ideally, set this to the interface's MAC address
            etherPacket.setDestinationMACAddress(MACAddress.valueOf(BROADCAST_MAC).toBytes());
            etherPacket.setPayload(ipPacket);
    
            // Send the packet on all interfaces
            for (Iface iface : this.interfaces.values()) {
                etherPacket.setSourceMACAddress(iface.getMacAddress().toBytes()); // Set the correct source MAC
                this.sendPacket(etherPacket, iface);
            }
        }

        private void sendRipPacket(RIPv2 ripPacket, Iface iface, String destinationIp) {
            UDP udpPacket = new UDP();
            udpPacket.setSourcePort((short) RIP_PORT);
            udpPacket.setDestinationPort((short) RIP_PORT);
            udpPacket.setPayload(ripPacket);
        
            IPv4 ipPacket = new IPv4();
            ipPacket.setTtl((byte) 1);  // TTL for RIP should be 1
            ipPacket.setProtocol(IPv4.PROTOCOL_UDP);
            ipPacket.setSourceAddress(iface.getIpAddress());  // Use the interface IP
            ipPacket.setDestinationAddress(IPv4.toIPv4Address(destinationIp));
            ipPacket.setPayload(udpPacket);
        
            Ethernet etherPacket = new Ethernet();
            etherPacket.setEtherType(Ethernet.TYPE_IPv4);
            etherPacket.setSourceMACAddress(iface.getMacAddress().toBytes());
            etherPacket.setDestinationMACAddress(MACAddress.valueOf(BROADCAST_MAC).toBytes());
            etherPacket.setPayload(ipPacket);
        
            this.sendPacket(etherPacket, iface);
        }

        public void sendRipResponse() {
            RIPv2 ripResponse = new RIPv2();
            ripResponse.setCommand(RIPv2.COMMAND_RESPONSE);
            
            for (RouteEntry entry : this.routeTable.getEntries()) {
                int address = entry.getDestinationAddress();
                int mask = entry.getMaskAddress();
                int metric = entry.getMetric();
                RIPv2Entry ripEntry = new RIPv2Entry(address, mask, metric);
                ripResponse.addEntry(ripEntry);
            }
        
            for (Iface iface : this.interfaces.values()) {
                sendRipPacket(ripResponse, iface, RIP_MULTICAST_IP);
            }
        }

        private void processRipPacket(Ethernet etherPacket, Iface inIface) {
            IPv4 ipPacket = (IPv4) etherPacket.getPayload();
            UDP udpPacket = (UDP) ipPacket.getPayload();
            RIPv2 ripPacket = (RIPv2) udpPacket.getPayload();
        
            int senderIp = ipPacket.getSourceAddress();
        
            if (ripPacket.getCommand() == RIPv2.COMMAND_REQUEST) {
                // Respond to RIP request
                sendRipResponse();
            } else if (ripPacket.getCommand() == RIPv2.COMMAND_RESPONSE) {
                // Update routing table based on RIP response, using the correct next-hop IP
                updateRoutingTable(ripPacket, inIface, senderIp);
            }
        }
        

        private void updateRoutingTable(RIPv2 ripPacket, Iface inIface, int senderIp) {

            for (RIPv2Entry entry : ripPacket.getEntries()) {
                int nextHopIp = senderIp;
                int networkAddress = entry.getAddress();
                int subnetMask = entry.getSubnetMask();
                int metric = entry.getMetric() + 1;
        
                RouteEntry existingEntry = this.routeTable.lookup(networkAddress);
                if (existingEntry == null || metric < existingEntry.getMetric()) {
                    this.routeTable.remove(networkAddress, subnetMask);
                    this.routeTable.insert(networkAddress, nextHopIp, subnetMask, inIface);
                }
            }
        }
        
       
        
        private void checkRouteTimeouts() {
            long currentTime = System.currentTimeMillis();
            // Assuming getEntries() exists, otherwise logic will need to adapt
            for (RouteEntry entry : new ArrayList<>(this.routeTable.getEntries())) {
                if (!this.interfaces.containsValue(entry.getInterface()) &&
                    currentTime - entry.getUpdateTime() > 30000) { // 30-second timeout
                    this.routeTable.remove(entry.getDestinationAddress(), entry.getMaskAddress());
                }
            }
        }

        
    }
