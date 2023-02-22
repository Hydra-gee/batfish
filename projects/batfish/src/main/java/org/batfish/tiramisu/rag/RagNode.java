package org.batfish.tiramisu.rag;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.batfish.symbolic.Protocol;

@Data
public class RagNode {
	  private String deviceId;
    private Protocol protocol;
    private boolean taint;
    private List<RagEdge> outEdges = new ArrayList<>();

    public RagNode(String deviceId, Protocol protocol,boolean taint){
        this.deviceId = deviceId;
        this.protocol = protocol;
        this.taint = taint;
    }

    public void addEdge(RagNode n,boolean is_ibgp){
        this.outEdges.add(new RagEdge(this,n,is_ibgp));
    }
}