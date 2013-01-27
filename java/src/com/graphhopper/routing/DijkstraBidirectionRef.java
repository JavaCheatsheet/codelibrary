package com.graphhopper.routing;

import com.graphhopper.routing.util.EdgeLevelFilter;
import com.graphhopper.storage.EdgeEntry;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GraphUtility;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.BitSet;
import java.util.PriorityQueue;

/**
 * Calculates extractPath path in bidirectional way.
 *
 * 'Ref' stands for reference implementation and is using the normal
 * Java-'reference'-way.
 *
 * @see DijkstraBidirection for an optimized but more complicated version
 * @author Peter Karich,
 */
public abstract class DijkstraBidirectionRef extends AbstractRoutingAlgorithm {

    private int from, to;
    private BitSet visitedFrom;
    private PriorityQueue<EdgeEntry> openSetFrom;
    private TIntObjectMap<EdgeEntry> shortestWeightMapFrom;
    private BitSet visitedTo;
    private PriorityQueue<EdgeEntry> openSetTo;
    private TIntObjectMap<EdgeEntry> shortestWeightMapTo;
    private boolean alreadyRun;
    protected EdgeEntry currFrom;
    protected EdgeEntry currTo;
    protected TIntObjectMap<EdgeEntry> shortestWeightMapOther;
    public PathBidirRef shortest;
    private EdgeLevelFilter edgeFilter;

    public DijkstraBidirectionRef(Graph graph) {
        super(graph);
        initCollections(Math.max(20, graph.nodes()));
    }

    protected void initCollections(int nodes) {
        visitedFrom = new BitSet(nodes);
        openSetFrom = new PriorityQueue<EdgeEntry>(nodes / 10);
        shortestWeightMapFrom = new TIntObjectHashMap<EdgeEntry>(nodes / 10);

        visitedTo = new BitSet(nodes);
        openSetTo = new PriorityQueue<EdgeEntry>(nodes / 10);
        shortestWeightMapTo = new TIntObjectHashMap<EdgeEntry>(nodes / 10);
    }

    public RoutingAlgorithm edgeFilter(EdgeLevelFilter edgeFilter) {
        this.edgeFilter = edgeFilter;
        return this;
    }

    public DijkstraBidirectionRef initFrom(int from) {
        this.from = from;
        currFrom = new EdgeEntry(EdgeIterator.NO_EDGE, from, 0);
        shortestWeightMapFrom.put(from, currFrom);
        visitedFrom.set(from);
        return this;
    }

    public DijkstraBidirectionRef initTo(int to) {
        this.to = to;
        currTo = new EdgeEntry(EdgeIterator.NO_EDGE, to, 0);
        shortestWeightMapTo.put(to, currTo);
        visitedTo.set(to);
        return this;
    }

    @Override public Path calcPath(int from, int to) {
        if (alreadyRun)
            throw new IllegalStateException("Call clear before! But this class is not thread safe!");

        alreadyRun = true;
        initPath();
        initFrom(from);
        initTo(to);

        Path p = checkIndenticalFromAndTo();
        if (p != null)
            return p;

        int finish = 0;
        while (finish < 2) {
            finish = 0;
            if (!fillEdgesFrom())
                finish++;

            if (!fillEdgesTo())
                finish++;
        }

        return extractPath();
    }

    public Path extractPath() {
        return shortest.extract();
    }

    public abstract boolean checkFinishCondition();

    void fillEdges(EdgeEntry curr, BitSet visitedMain, PriorityQueue<EdgeEntry> prioQueue,
            TIntObjectMap<EdgeEntry> shortestWeightMap, boolean out) {

        int currNodeFrom = curr.endNode;                
        EdgeIterator iter = GraphUtility.getEdges(graph, currNodeFrom, out);
        if (edgeFilter != null)
            iter = edgeFilter.doFilter(iter);
        
        while (iter.next()) {
            int neighborNode = iter.node();
            if (visitedMain.get(neighborNode))
                continue;

            double tmpWeight = iter.distance() + curr.weight;
            EdgeEntry de = shortestWeightMap.get(neighborNode);
            if (de == null) {
                de = new EdgeEntry(iter.edge(), neighborNode, tmpWeight);
                de.parent = curr;
                shortestWeightMap.put(neighborNode, de);
                prioQueue.add(de);
            } else if (de.weight > tmpWeight) {
                prioQueue.remove(de);
                de.edge = iter.edge();
                de.weight = tmpWeight;
                de.parent = curr;
                prioQueue.add(de);
            }

            updateShortest(de, neighborNode);
        }
    }

    @Override
    protected void updateShortest(EdgeEntry shortestDE, int currLoc) {
        EdgeEntry entryOther = shortestWeightMapOther.get(currLoc);
        if (entryOther == null)
            return;

        // update μ
        double newShortest = shortestDE.weight + entryOther.weight;
        if (newShortest < shortest.distance) {
            shortest.switchToFrom(shortestWeightMapFrom == shortestWeightMapOther);
            shortest.edgeEntry = shortestDE;
            shortest.edgeTo = entryOther;
            shortest.distance = newShortest;
        }
    }

    public boolean fillEdgesFrom() {
        if (currFrom != null) {
            shortestWeightMapOther = shortestWeightMapTo;
            fillEdges(currFrom, visitedFrom, openSetFrom, shortestWeightMapFrom, true);
            if (openSetFrom.isEmpty()) {
                currFrom = null;
                return false;
            }

            currFrom = openSetFrom.poll();
            if (checkFinishCondition())
                return false;
            visitedFrom.set(currFrom.endNode);
        } else if (currTo == null)
            return false;
        return true;
    }

    public boolean fillEdgesTo() {
        if (currTo != null) {
            shortestWeightMapOther = shortestWeightMapFrom;
            fillEdges(currTo, visitedTo, openSetTo, shortestWeightMapTo, false);
            if (openSetTo.isEmpty()) {
                currTo = null;
                return false;
            }

            currTo = openSetTo.poll();
            if (checkFinishCondition())
                return false;
            visitedTo.set(currTo.endNode);
        } else if (currFrom == null)
            return false;
        return true;
    }

    private Path checkIndenticalFromAndTo() {
        if (from == to)
            return new Path(graph);
        return null;
    }

    protected PathBidirRef createPath() {
        return new PathBidirRef(graph);
    }

    public DijkstraBidirectionRef initPath() {
        shortest = createPath();
        return this;
    }
}