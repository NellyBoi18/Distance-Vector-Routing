package edu.wisc.cs.sdn.vnet;

import edu.wisc.cs.sdn.vnet.rt.Router;
import edu.wisc.cs.sdn.vnet.sw.Switch;
import edu.wisc.cs.sdn.vnet.vns.Command;
import edu.wisc.cs.sdn.vnet.vns.VNSComm;

public class Main {
    private static final short DEFAULT_PORT = 8888;
    private static final String DEFAULT_SERVER = "localhost";

    public static void main(String[] args) {
        String host = null;
        String server = DEFAULT_SERVER;
        String routeTableFile = null;
        String arpCacheFile = null;
        String logfile = null;
        short port = DEFAULT_PORT;

        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-h")) {
                usage();
                return;
            } else if (arg.equals("-p")) {
                port = Short.parseShort(args[++i]);
            } else if (arg.equals("-v")) {
                host = args[++i];
            } else if (arg.equals("-s")) {
                server = args[++i];
            } else if (arg.equals("-l")) {
                logfile = args[++i];
            } else if (arg.equals("-r")) {
                routeTableFile = args[++i];
            } else if (arg.equals("-a")) {
                arpCacheFile = args[++i];
            }
        }

        if (null == host) {
            usage();
            return;
        }

        // Open PCAP dump file for logging packets sent/received by the device
        DumpFile dump = logfile != null ? DumpFile.open(logfile) : null;
        if (logfile != null && dump == null) {
            System.err.println("Error opening up dump file " + logfile);
            return;
        }

        Device dev = null;
        if (host.startsWith("s")) {
            dev = new Switch(host, dump);
        } else if (host.startsWith("r")) {
            dev = new Router(host, dump);
        } else {
            System.err.println("Device name must start with 's' or 'r'");
            return;
        }

        // Connect to Virtual Network Simulator server and negotiate session
        System.out.println(String.format("Connecting to server %s:%d", server, port));
        VNSComm vnsComm = new VNSComm(dev);
        if (!vnsComm.connectToServer(port, server)) {
            System.exit(1);
        }
        vnsComm.readFromServerExpect(Command.VNS_HW_INFO);

        // Initialization for router after successful VNS communication
        if (dev instanceof Router) {
            Router router = (Router) dev;
            if (routeTableFile != null) {
                router.loadRouteTable(routeTableFile);
            }
            if (arpCacheFile != null) {
                router.loadArpCache(arpCacheFile);
            }
            if (routeTableFile == null) {
                router.initializeRIP();
            }
        }

        // Ready to process packets
        System.out.println("<-- Ready to process packets -->");
        while (vnsComm.readFromServer());

        // Shutdown the device
        dev.destroy();
    }

    static void usage() {
        System.out.println("Virtual Network Client");
        System.out.println("VNet -v host [-s server] [-p port] [-h]");
        System.out.println("     [-r routing_table] [-a arp_cache] [-l log_file]");
        System.out.println(String.format("  defaults server=%s port=%d", DEFAULT_SERVER, DEFAULT_PORT));
    }
}
