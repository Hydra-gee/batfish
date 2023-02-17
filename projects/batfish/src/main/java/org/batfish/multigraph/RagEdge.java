package org.batfish.multigraph;

public class RagEdge {
    RagNode src;
    RagNode dst;
    protocol type;

    public RagEdge(RagNode s, RagNode v){
        src = s; dst = v; type = protocol.NONE;
    }

    public RagEdge(RagNode s, RagNode v, protocol p){
        src = s; dst = v; type = p;
    }

    public RagNode getDst() {
        return dst;
    }

    public RagNode getSrc() {
        return src;
    }

    public protocol getType() {
        return type;
    }

    @Override
    public String toString() {
        return "Edge [src=" + src + ", dst=" + dst + " type: " + type  + "]";
    }

    public RagEdge copy() {
        return new RagEdge(src, dst, type);
    }

}

