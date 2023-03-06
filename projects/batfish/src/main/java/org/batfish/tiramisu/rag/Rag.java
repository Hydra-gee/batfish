package org.batfish.tiramisu.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.batfish.symbolic.Protocol;

@Data
public class Rag {
    private int nodeNum = 0;
    private int edgeNum = 0;
    private RagNode srcNode = null;
    private RagNode dstNode = null;
    private List<RagNode> nodeList = new ArrayList<>();
    private Map<String,Map<Protocol,RagNode>> nodeMap = new HashMap<>();
    private boolean taint;

    public void print(){
      String s = "";
      for(String router:nodeMap.keySet()){
        for(Protocol p : nodeMap.get(router).keySet()){
          System.out.println("------------------");
          RagNode t_node = nodeMap.get(router).get(p);
          System.out.println(t_node.getDeviceId() + "_" + t_node.getProtocol().name() + ",taint:" + t_node.isTaint());
          System.out.println("edges:");
          for(RagEdge neighbor:t_node.getOutEdges()){
            System.out.println(neighbor.getDst().getDeviceId() + "_" + neighbor.getDst().getProtocol().name());
          }
        }
      }
    }

    public void addNode(RagNode node) {
        if(!this.nodeMap.containsKey(node.getDeviceId())){
            this.nodeMap.put(node.getDeviceId(), new HashMap<>());
        }
        if(!this.nodeMap.get(node.getDeviceId()).containsKey(node.getProtocol())){
            this.nodeMap.get(node.getDeviceId()).put(node.getProtocol(),node);
        }
    }

    public void addEdge(RagNode from, RagNode to, boolean is_ibgp) {
        this.addNode(from);
        this.addNode(to);
        this.nodeMap.get(from.getDeviceId()).get(from.getProtocol()).addEdge(to,is_ibgp);
    }

    public void taint() {
      taint_propagate(dstNode, false);
    }

    public void taint_propagate(RagNode r, boolean ibgp) {
      r.setTaint(true);
      for (RagEdge e :  r.getOutEdges()) {
        if (e.isIbgp()) {
          if (!ibgp && !e.getDst().isTaint()) {
            taint_propagate(e.getDst(), true);
          }
        } else {
          if (!e.getDst().isTaint()) {
            taint_propagate(e.getDst(), false);
          }
        }
      }
    }
}

