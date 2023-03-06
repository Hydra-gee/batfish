package org.batfish.tiramisu.tpg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import org.batfish.symbolic.Protocol;
import org.batfish.tiramisu.NodeType;

@Data
public class Tpg {
    private int nodeNum = 0;
    private TpgNode srcNode = null;
    private TpgNode dstNode = null;
    private Map<String,Map<Protocol,Map<NodeType,TpgNode>>> nodeMap = new HashMap<>();
    private Set<TpgNode> nodeSet = new LinkedHashSet<>();

//  public void addNode(TpgNode node){
//      if(!nodeMap.containsKey(node.getDeviceId())){
//        this.nodeMap.put(node.getDeviceId(),new HashMap<>());
//      }
//      if(!nodeMap.get(node.getDeviceId()).containsKey(node.getProtocol())){
//        this.nodeMap.get(node.getDeviceId())
//                    .put(node.getProtocol(),new HashMap<>());
//      }
//      this.nodeMap.get(node.getDeviceId())
//                  .get(node.getProtocol())
//                  .put(node.getVlanType(),node);
//    }

    public void addNode(TpgNode node){
      this.nodeSet.add(node);
    }

    public void addEdge(TpgNode node1,TpgNode node2){
      node1.getOutEdges().add(new TpgEdge(node1,node2));
    }

    public List<TpgNode> selectNodes(String router,Protocol protocol,NodeType vlanType,String vlanPeer){
      List<TpgNode> resultSet = new ArrayList<>();
      for(TpgNode node:nodeSet){
        if(node.compareTo(new TpgNode(router,protocol,vlanType,vlanPeer))){
          resultSet.add(node);
        }
      }
      return resultSet;
    }
}

