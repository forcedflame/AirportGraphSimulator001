import java.util.*;

public class StudentCode extends Server {
    private AirportGraph graph = new AirportGraph();
    private Airport originAirport = null;
    private Airport destAirport = null;

    private int requiredLayovers = 2;
    private String metric = "distance"; // "distance" | "time" | "cost"

    private static final String DIJKSTRA_COLOR = "rgb(59,130,246)";
    private static final String ASTAR_COLOR = "rgb(249,115,22)";
    private static final String ORIGIN_COLOR = "rgb(34,197,94)";
    private static final String DEST_COLOR = "rgb(220,38,38)";
    private static final String SHARED_COLOR = "rgb(168,85,247)";

    public static void main(String[] args) {
        StudentCode server = new StudentCode();

        server.graph = new AirportGraph();
        server.graph.loadFromCSV("airports.csv");
        server.graph.loadRoutesFromCSV("routes.csv");

        System.out.println("Loaded " + server.graph.getAllAirports().size() + " airports.");

        server.run();
        server.openURL();
    }

    @Override
    public void getInputCountries(String originCode, String destinationCode) {
        clearCountryColors();
        originAirport = null;
        destAirport   = null;

        Airport origin = graph.getAirport(originCode);
        Airport dest   = graph.getAirport(destinationCode);
        
        if (origin == null) {
            setMessage("Airport code \"" + originCode + "\" not found.");
            return;
        }
        if (dest == null) {
            setMessage("Airport code \"" + destinationCode + "\" not found.");
            return;
        }
        if (origin.equals(dest)) {
            setMessage("Origin and destination cannot be the same airport.");
            return;
        }
        if (!graph.isConnected(origin, dest)) {
            setMessage("No route exists between " + originCode + " and " + destinationCode + ".");
            return;
        }

        int requiredHops = requiredLayovers + 1;

        AirportGraph.PathResult dijkstraResult = graph.dijkstraExact(origin, dest, requiredHops, metric);

        AirportGraph.PathResult aStarResult = graph.aStarExact(origin, dest, requiredHops, metric);

        if (dijkstraResult.isEmpty() && aStarResult.isEmpty()) {
            setMessage("No route found with exactly " + requiredLayovers + " layover(s).");
            return;
        }

        colorLayovers(dijkstraResult, DIJKSTRA_COLOR);
        colorLayovers(aStarResult, ASTAR_COLOR);
        markSharedLayovers(dijkstraResult, aStarResult);
        addCountryColor(origin.getCode(), ORIGIN_COLOR);
        addCountryColor(dest.getCode(), DEST_COLOR);

        setMessage(buildComparisonMessage(dijkstraResult, aStarResult, origin, dest));

        System.out.println(dijkstraResult);
        System.out.println(aStarResult);
        printCountryColors();
    }

    @Override
    public void getColorPath() {
        originAirport = null;
        destAirport = null;
        clearCountryColors();
        setMessage("Reset complete. Select an origin and destination airport.");
    }

    @Override
    public void handleClick(String airportCode) {
        if ("__RESET__".equals(airportCode)) {
            originAirport = null;
            destAirport   = null;
            clearCountryColors();
            setMessage("Reset complete.");
            return;
        }

        Airport clicked = graph.getAirport(airportCode);

        if (clicked == null) {
            setMessage("\"" + airportCode + "\" is not a recognised airport code.");
            return;
        }

        if (originAirport == null) {
            originAirport = clicked;
            clearCountryColors();
            addCountryColor(clicked.getCode(), ORIGIN_COLOR);
            setMessage("Origin set: " + clicked.getCode() + " — " + clicked.getName() + "\nNow click a destination airport.");
            return;
        }

        if (clicked.equals(originAirport)) {
            originAirport = null;
            clearCountryColors();
            setMessage("Origin deselected.");
            return;
        }

        destAirport = clicked;
        getInputCountries(originAirport.getCode(), destAirport.getCode());

        originAirport = null;
        destAirport = null;
    }

    public void setRequiredLayovers(int layovers) {
        this.requiredLayovers = layovers;
    }
    public void updateLayovers(int layovers) {
        this.requiredLayovers = layovers;
    }

    public void updateMetric(String metric) {
        if ("time".equals(metric) || "cost".equals(metric) || "distance".equals(metric))
            this.metric = metric;
    }

    private void colorLayovers(AirportGraph.PathResult result, String color) {
        if (result.path == null || result.path.size() < 3) return;
        for (int i = 1; i < result.path.size() - 1; i++)
            addCountryColor(result.path.get(i).getCode(), color);
    }

    private void markSharedLayovers(AirportGraph.PathResult d, AirportGraph.PathResult a) {
        Set<String> dLayovers = layoverCodes(d);
        Set<String> aLayovers = layoverCodes(a);
        dLayovers.retainAll(aLayovers);
        for (String code : dLayovers)
            addCountryColor(code, SHARED_COLOR);
    }

    private Set<String> layoverCodes(AirportGraph.PathResult result) {
        Set<String> codes = new HashSet<>();
        if (result.path == null || result.path.size() < 3) return codes;
        for (int i = 1; i < result.path.size() - 1; i++)
            codes.add(result.path.get(i).getCode());
        return codes;
    }

    private String buildComparisonMessage(AirportGraph.PathResult dijkstra, AirportGraph.PathResult aStar, Airport origin, Airport dest) {
        String metricLabel = metric.substring(0, 1).toUpperCase() + metric.substring(1);
        StringBuilder sb = new StringBuilder();

        sb.append("Route: ").append(origin.getCode()).append(" \u2192 ").append(dest.getCode()).append("\n");
        sb.append("Metric: ").append(metricLabel).append("\n");
        sb.append("Layovers: ").append(requiredLayovers).append("\n\n");

        //Dijkstra
        sb.append("── Dijkstra (blue) ──\n");
        sb.append(formatPath(dijkstra)).append("\n");
        sb.append(String.format("Distance : %.0f km\n",      dijkstra.totalDistance));
        sb.append(String.format("Time     : %s\n",           formatTime(dijkstra.totalTime)));
        sb.append(String.format("Cost     : $%.2f\n",        dijkstra.totalCost));
        sb.append("Optimised: ").append(dijkstra.primarySummary()).append("\n");
        sb.append("Explored : ").append(dijkstra.nodesExplored).append(" nodes\n\n");

        //A*
        sb.append("── A* (orange) ──\n");
        sb.append(formatPath(aStar)).append("\n");
        sb.append(String.format("Distance : %.0f km\n",      aStar.totalDistance));
        sb.append(String.format("Time     : %s\n",           formatTime(aStar.totalTime)));
        sb.append(String.format("Cost     : $%.2f\n",        aStar.totalCost));
        sb.append("Optimised: ").append(aStar.primarySummary()).append("\n");
        sb.append("Explored : ").append(aStar.nodesExplored).append(" nodes\n\n");

        int saved = dijkstra.nodesExplored - aStar.nodesExplored;
        if (saved > 0)
            sb.append("A* explored ").append(saved).append(" fewer nodes.");
        else if (saved < 0)
            sb.append("Dijkstra explored ").append(-saved).append(" fewer nodes.");
        else
            sb.append("Both explored the same number of nodes.");

        return sb.toString();
    }

    private String formatPath(AirportGraph.PathResult result) {
        if (result.isEmpty()) return "No valid path";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < result.path.size(); i++) {
            sb.append(result.path.get(i).getCode());
            if (i < result.path.size() - 1) sb.append(" \u2192 ");
        }
        return sb.toString();
    }

    private static String formatTime(double minutes) {
        if (minutes <= 0) return "N/A";
        int h = (int) minutes / 60;
        int m = (int) minutes % 60;
        return h > 0 ? h + "h " + m + "m" : m + "m";
    }
}