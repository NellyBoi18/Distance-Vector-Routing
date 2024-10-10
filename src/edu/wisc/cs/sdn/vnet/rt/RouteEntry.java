package edu.wisc.cs.sdn.vnet.rt;

import net.floodlightcontroller.packet.IPv4;
import edu.wisc.cs.sdn.vnet.Iface;

/**
 * An entry in a route table.
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class RouteEntry {
    /** Destination IP address */
    private int destinationAddress;

    /** Gateway IP address */
    private int gatewayAddress;

    /** Subnet mask */
    private int maskAddress;

    /** Router interface out which packets should be sent to reach
     * the destination or gateway */
    private Iface iface;

    /** Last update time for this route entry */
    private long updateTime;

    private int metric; // Add metric field


    /**
     * Create a new route table entry.
     * @param destinationAddress destination IP address
     * @param gatewayAddress gateway IP address
     * @param maskAddress subnet mask
     * @param iface the router interface out which packets should 
     *        be sent to reach the destination or gateway
     */
    public RouteEntry(int destinationAddress, int gatewayAddress, 
                      int maskAddress, Iface iface) {
        this.destinationAddress = destinationAddress;
        this.gatewayAddress = gatewayAddress;
        this.maskAddress = maskAddress;
        this.iface = iface;
        this.updateTime = System.currentTimeMillis(); // Initialize update time
    }

    /**
     * @return destination IP address
     */
    public int getDestinationAddress() {
        return this.destinationAddress;
    }

    public int getMetric() {
        return this.metric;
    }

    public void setMetric(int metric) {
        this.metric = metric;
    }

    /**
     * @return gateway IP address
     */
    public int getGatewayAddress() {
        return this.gatewayAddress;
    }

    public void setGatewayAddress(int gatewayAddress) {
        this.gatewayAddress = gatewayAddress;
        this.updateUpdateTime(); // Update time on modification
    }

    /**
     * @return subnet mask 
     */
    public int getMaskAddress() {
        return this.maskAddress;
    }

    /**
     * @return the router interface out which packets should be sent to 
     *         reach the destination or gateway
     */
    public Iface getInterface() {
        return this.iface;
    }

    public void setInterface(Iface iface) {
        this.iface = iface;
        this.updateUpdateTime(); // Update time on modification
    }

    /**
     * Gets the last update time of this route entry.
     * @return the last update time in milliseconds since epoch
     */
    public long getUpdateTime() {
        return this.updateTime;
    }

    /**
     * Updates the last update time of this route entry to the current time.
     */
    public void updateUpdateTime() {
        this.updateTime = System.currentTimeMillis();
    }

    public String toString() {
        return String.format("%s \t%s \t%s \t%s",
                             IPv4.fromIPv4Address(this.destinationAddress),
                             IPv4.fromIPv4Address(this.gatewayAddress),
                             IPv4.fromIPv4Address(this.maskAddress),
                             this.iface.getName());
    }
}

