import java.io.*;
import java.util.*;

public class AirportGraph {
    private Map<Airport, List<Edge>> adjList;

    public static class Edge {
        Airport destination;
        double distanceKm;
        double timeMinutes;
        double costUSD;

        public Edge(Airport destination, double distanceKm, double timeMinutes, double costUSD) {
            this.destination = destination;
            this.distanceKm = distanceKm;
            this.timeMinutes = timeMinutes;
            this.costUSD = costUSD;
        }

        public double weight(String metric) {
            switch (metric) {
                case "time": return timeMinutes;
                case "cost": return costUSD;
                default: return distanceKm;
            }
        }

        public String toString() {
            return destination.getCode() + String.format(" (%.0f km | %.0f min | $%.2f)", distanceKm, timeMinutes, costUSD);
        }
    }

    private static class State {
        Airport airport;
        int hops;

        State(Airport a, int h) { airport = a; hops = h; }

        public boolean equals(Object o) {
            if (!(o instanceof State)) return false;
            State s = (State) o;
            return airport.equals(s.airport) && hops == s.hops;
        }

        public int hashCode() { return Objects.hash(airport, hops); }
    }

    public AirportGraph() { adjList = new HashMap<>(); }

    public void addAirport(Airport airport) {
        adjList.putIfAbsent(airport, new ArrayList<>());
    }

    public void addRoute(Airport from, Airport to, double distKm, double timeMin, double cost) {
        if (!adjList.containsKey(from) || !adjList.containsKey(to)) return;
        adjList.get(from).add(new Edge(to, distKm, timeMin, cost));
    }

    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public PathResult dijkstraExact(Airport start, Airport end, int requiredHops, String metric) {
        Map<State, Double> dist = new HashMap<>();
        Map<State, State>  prev = new HashMap<>();

        PriorityQueue<State> pq = new PriorityQueue<>(Comparator.comparingDouble(s -> dist.getOrDefault(s, Double.MAX_VALUE)));

        State startState = new State(start, 0);
        dist.put(startState, 0.0);
        pq.add(startState);

        int nodesExplored = 0;
        State bestState   = null;

        while (!pq.isEmpty()) {
            State curr = pq.poll();
            nodesExplored++;

            if (curr.airport.equals(end) && curr.hops == requiredHops) {
                if (bestState == null || dist.get(curr) < dist.get(bestState))
                    bestState = curr;
                continue;
            }

            if (curr.hops >= requiredHops) continue;

            for (Edge edge : adjList.getOrDefault(curr.airport, Collections.emptyList())) {
                State  next = new State(edge.destination, curr.hops + 1);
                double newDist = dist.get(curr) + edge.weight(metric);

                if (newDist < dist.getOrDefault(next, Double.MAX_VALUE)) {
                    dist.put(next, newDist);
                    prev.put(next, curr);
                    pq.add(next);
                }
            }
        }

        if (bestState == null)
            return new PathResult(new ArrayList<>(), -1, -1, -1, nodesExplored, "Dijkstra", metric);

        return buildStateResult(prev, bestState, dist.get(bestState), nodesExplored, "Dijkstra", metric);
    }

    public PathResult aStarExact(Airport start, Airport end, int requiredHops, String metric) {
        Map<State, Double> gScore = new HashMap<>();
        Map<State, Double> fScore = new HashMap<>();
        Map<State, State>  prev   = new HashMap<>();

        PriorityQueue<State> pq = new PriorityQueue<>(Comparator.comparingDouble(s -> fScore.getOrDefault(s, Double.MAX_VALUE)));

        State startState = new State(start, 0);
        gScore.put(startState, 0.0);
        fScore.put(startState, heuristic(start, end, metric));
        pq.add(startState);

        int nodesExplored = 0;
        State bestState   = null;

        while (!pq.isEmpty()) {
            State curr = pq.poll();
            nodesExplored++;

            if (curr.airport.equals(end) && curr.hops == requiredHops) {
                if (bestState == null || gScore.get(curr) < gScore.get(bestState))
                    bestState = curr;
                continue;
            }

            if (curr.hops >= requiredHops) continue;

            for (Edge edge : adjList.getOrDefault(curr.airport, Collections.emptyList())) {
                State next = new State(edge.destination, curr.hops + 1);
                double tentativeG = gScore.get(curr) + edge.weight(metric);

                if (tentativeG < gScore.getOrDefault(next, Double.MAX_VALUE)) {
                    gScore.put(next, tentativeG);
                    fScore.put(next, tentativeG + heuristic(edge.destination, end, metric));
                    prev.put(next, curr);
                    pq.add(next);
                }
            }
        }

        if (bestState == null)
            return new PathResult(new ArrayList<>(), -1, -1, -1, nodesExplored, "A*", metric);

        return buildStateResult(prev, bestState, gScore.get(bestState), nodesExplored, "A*", metric);
    }

    private double heuristic(Airport from, Airport to, String metric) {
        double km = haversine(from.getLat(), from.getLon(), to.getLat(), to.getLon());
        switch (metric) {
            case "time": return (km / 850.0) * 60.0;
            case "cost": return km * 0.12;
            default:     return km;
        }
    }

    private PathResult buildStateResult(Map<State, State> prev, State endState, double primaryMetric, int nodesExplored, String algo, String metric) {
        List<Airport> path = new ArrayList<>();
        double totalDist = 0;
        double totalTime = 0;
        double totalCost = 0;

        State curr = endState;
        List<State> stateChain = new ArrayList<>();
        while (curr != null) {
            stateChain.add(curr);
            curr = prev.get(curr);
        }
        Collections.reverse(stateChain);

        for (int i = 0; i < stateChain.size(); i++) {
            path.add(stateChain.get(i).airport);

            if (i > 0) {
                Airport from = stateChain.get(i - 1).airport;
                Airport to   = stateChain.get(i).airport;

                for (Edge e : adjList.getOrDefault(from, Collections.emptyList())) {
                    if (e.destination.equals(to)) {
                        totalDist += e.distanceKm;
                        totalTime += e.timeMinutes;
                        totalCost += e.costUSD;
                        break;
                    }
                }
            }
        }

        return new PathResult(path, totalDist, totalTime, totalCost, nodesExplored, algo, metric);
    }

    public boolean isConnected(Airport a, Airport b) {
        if (!adjList.containsKey(a) || !adjList.containsKey(b)) return false;

        Set<Airport>   visited = new HashSet<>();
        Queue<Airport> queue   = new LinkedList<>();
        queue.add(a);
        visited.add(a);

        while (!queue.isEmpty()) {
            Airport c = queue.poll();
            if (c.equals(b)) return true;
            for (Edge edge : adjList.getOrDefault(c, Collections.emptyList()))
                if (!visited.contains(edge.destination)) {
                    visited.add(edge.destination);
                    queue.add(edge.destination);
                }
        }
        return false;
    }

    public Airport getAirport(String code) {
        for (Airport a : adjList.keySet())
            if (a.getCode().equalsIgnoreCase(code.trim())) return a;
        return null;
    }

    public List<Airport> getAllAirports() {
        return new ArrayList<>(adjList.keySet());
    }

    public void loadFromCSV(String fileName) {
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 6) continue;

                String code    = parts[0].trim();
                String name    = parts[1].trim();
                String city    = parts[2].trim();
                String country = parts[3].trim();

                double lat, lon;
                try {
                    lat = Double.parseDouble(parts[4].trim());
                    lon = Double.parseDouble(parts[5].trim());
                } catch (Exception e) { continue; }

                addAirport(new Airport(code, name, city, country, lat, lon));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void loadRoutesFromCSV(String fileName) {
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;
            br.readLine();

            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                if (parts.length < 4) continue;

                Airport from = getAirport(parts[0].trim());
                Airport to   = getAirport(parts[1].trim());
                if (from == null || to == null) continue;

                double timeMin = parseFlightTime(parts[2].trim());
                double cost    = parseCost(parts[3].trim());
                double distKm  = haversine(from.getLat(), from.getLon(), to.getLat(),   to.getLon());

                addRoute(from, to, distKm, timeMin, cost);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static double parseFlightTime(String raw) {
        raw = raw.replaceAll("\"", "").trim();
        double minutes = 0;
        java.util.regex.Matcher mH = java.util.regex.Pattern.compile("(\\d+)\\s*h").matcher(raw);
        java.util.regex.Matcher mM = java.util.regex.Pattern.compile("(\\d+)\\s*m").matcher(raw);
        if (mH.find()) minutes += Integer.parseInt(mH.group(1)) * 60;
        if (mM.find()) minutes += Integer.parseInt(mM.group(1));
        return minutes == 0 ? 60 : minutes;
    }

    private static double parseCost(String raw) {
        raw = raw.replaceAll("[\"$,]", "").trim();
        try { return Double.parseDouble(raw); }
        catch (Exception e) { return 0.0; }
    }

    public static class PathResult {
        public List<Airport> path;
        public double totalDistance;
        public double totalTime;
        public double totalCost;
        public int nodesExplored;
        public String algorithm;
        public String metric;

        public PathResult(List<Airport> path, double totalDistance, double totalTime, double totalCost, int nodesExplored, String algorithm, String metric) {
            this.path = path;
            this.totalDistance = totalDistance;
            this.totalTime = totalTime;
            this.totalCost = totalCost;
            this.nodesExplored = nodesExplored;
            this.algorithm = algorithm;
            this.metric = metric;
        }

        public boolean isEmpty() {
            return path == null || path.isEmpty();
        }

        public String primarySummary() {
            switch (metric) {
                case "time": return formatTime(totalTime);
                case "cost": return String.format("$%.2f", totalCost);
                default: return String.format("%.0f km", totalDistance);
            }
        }

        private static String formatTime(double minutes) {
            int h = (int) minutes / 60;
            int m = (int) minutes % 60;
            return h > 0 ? h + "h " + m + "m" : m + "m";
        }

        public String toJSON() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"algorithm\":\"").append(algorithm).append("\",");
            sb.append("\"metric\":\"").append(metric).append("\",");
            sb.append("\"totalDistance\":").append(String.format("%.2f", totalDistance)).append(",");
            sb.append("\"totalTime\":").append(String.format("%.2f", totalTime)).append(",");
            sb.append("\"totalCost\":").append(String.format("%.2f", totalCost)).append(",");
            sb.append("\"nodesExplored\":").append(nodesExplored).append(",");
            sb.append("\"path\":[");
            for (int i = 0; i < path.size(); i++) {
                Airport a = path.get(i);
                sb.append("{").append("\"code\":\"").append(a.getCode()).append("\",").append("\"name\":\"").append(a.getName()).append("\",").append("\"lat\":").append(a.getLat()).append(",").append("\"lon\":").append(a.getLon()).append("}");
                if (i < path.size() - 1) sb.append(",");
            }
            sb.append("]}");
            return sb.toString();
        }

        public String toString() {
            if (isEmpty()) return algorithm + ": No path found.";
            StringBuilder sb = new StringBuilder(algorithm + ": ");
            for (int i = 0; i < path.size(); i++) {
                sb.append(path.get(i).getCode());
                if (i < path.size() - 1) sb.append(" -> ");
            }
            sb.append(String.format(" | %.0f km | %s | $%.2f | %d nodes", totalDistance, PathResult.formatTime(totalTime), totalCost, nodesExplored));
            return sb.toString();
        }
    }
}