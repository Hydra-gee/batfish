package org.batfish.tiramisu.rag;

import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.DeviceType;
import org.batfish.datamodel.IpWildcard;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.symbolic.Graph;
import org.batfish.symbolic.GraphEdge;
import org.batfish.symbolic.Protocol;

public class buildRag implements Runnable {

  Graph g;
  Rag rag;

  IpWildcard srcIp;
  IpWildcard dstIp;

  public RagNode srcTC = null;
  public RagNode dstTC = null;

  String srcNodeName = null;
  String dstNodeName = null;

  public buildRag(Graph g, IpWildcard srcip, IpWildcard dstip) {
    this.g = g;
    rag = new Rag();
    srcIp = srcip;
    dstIp = dstip;
  }

  public buildRag(Graph g, String src, String dst, IpWildcard srcip, IpWildcard dstip) {
    this.g = g;
    rag = new Rag();
    srcNodeName = src;
    dstNodeName = dst;
    srcIp = srcip;
    dstIp = dstip;
  }

  public Rag getRag() {
    return rag;
  }

  public void buildGraph() {
    initial();
    buildNodes();
    setSrcDst();
    buildEdges();
  }

  @Override
  public void run() {
    buildGraph();
//    rag.taint();
  }

  public void initial(){

  }

  public void buildNodes(){
    // create physical router to protocols mapping
    g.getConfigurations().forEach((router, conf) ->{
      if (conf.getDeviceType() != DeviceType.SWITCH) {
        if (conf.getDefaultVrf().getOspfProcess() != null) {
          this.rag.addNode(new RagNode(router, Protocol.OSPF,false));
        }
        if (conf.getDefaultVrf().getBgpProcess() != null) {
          this.rag.addNode(new RagNode(router, Protocol.BGP,false));
        }
//        if (!conf.getDefaultVrf().getStaticRoutes().isEmpty()) {
//          this.rag.addNode(new RagNode(router, NodeType.STATIC,false));
//        }
      }
    });
  }

  //TODO:暂时删除了重分发和过滤策略
  public void buildEdges(){
    RagNode srcNode, dstNode;
    String srcName, dstName;
    //同一类路由协议的邻接边
    for (Entry<String, List<GraphEdge>> entry : g.getEdgeMap().entrySet()) {
      String router = entry.getKey();
      List<GraphEdge> edges = entry.getValue();
      Configuration conf = g.getConfigurations().get(router);
      for (Protocol proto : rag.getNodeMap().get(router).keySet()) {
        for (GraphEdge e : edges) {
          if (g.isEdgeUsed(conf, proto, e)) {
            srcName = e.getRouter();
            dstName = e.getPeer();
            if (srcName == null || dstName == null) {
              continue;
            }
            if (proto.isBgp()) {
              srcNode = rag.getNodeMap().get(srcName).get(Protocol.BGP);
              dstNode = rag.getNodeMap().get(dstName).get(Protocol.BGP);
              if (g.getIbgpNeighbors().containsKey(e)) {
                rag.addEdge(srcNode, dstNode, true);
              } else {
                rag.addEdge(srcNode, dstNode, false);
              }
            } else if (proto.isOspf()) {
              srcNode = rag.getNodeMap().get(srcName).get(Protocol.OSPF);
              dstNode = rag.getNodeMap().get(dstName).get(Protocol.OSPF);
              rag.addEdge(srcNode, dstNode, false);
            }
          }
        }
      }
    }
    //路由重分发产生的边
    for (Entry<String, Configuration> entry : g.getConfigurations().entrySet()) {
      String router = entry.getKey();
      Configuration conf = entry.getValue();
      for (Protocol proto : rag.getNodeMap().get(router).keySet()) {
        RoutingPolicy pol = Graph.findCommonRoutingPolicy(conf, proto);
        if (pol == null) {
          continue;
        }
        Set<Protocol> ps = g.findRedistributedProtocols(conf, pol, proto);

        for (Protocol p : ps) {
//          protocol dstType = protocol.NONE;
//          if (p.isBgp()) {
//            dstnode = bgpName(router);
//            dstType = protocol.BGP;
//          }
//          else if (p.isOspf()) {
//            dstnode = ospfName(router);
//            dstType = protocol.OSPF;
//          } else {
//            continue;
//          }
//
//          dst = multigraphNode.get(dstnode);
//          if (src == null || dst == null) {
//            continue;
//          }
//          rag.add(src, dst, protocol.BGP);
        }
      }
    }
  }

  public void setSrcDst() {
    if (srcNodeName == null || srcNodeName.equals("") || dstNodeName == null || dstNodeName.equals(
        "")) {
      for (Entry<String, List<GraphEdge>> entry : g.getEdgeMap().entrySet()) {
        String router = entry.getKey();
        Configuration conf = g.getConfigurations().get(router);
        Set<Prefix> prefixes = Graph.getOriginatedNetworks(conf, Protocol.CONNECTED);
        for (Prefix pp : prefixes) {
          if (pp.containsPrefix(srcIp.toPrefix())) {
            srcNodeName = router;
          }
          if (pp.containsPrefix(dstIp.toPrefix())) {
            dstNodeName = router;
          }
        }
      }
    }

    if (srcNodeName != null && rag.getNodeMap().containsKey(srcNodeName)) {
      RagNode srcNode = new RagNode(srcNodeName, Protocol.CONNECTED,false);
      rag.addNode(srcNode);
      for(RagNode n:rag.getNodeMap().get(srcNodeName).values()){
        if(!n.equals(srcNode))
        rag.addEdge(n,srcNode,false);
      }
      rag.setSrcNode(srcNode);
    }
    if (dstNodeName != null && rag.getNodeMap().containsKey(dstNodeName)) {
      RagNode dstNode = new RagNode(dstNodeName, Protocol.CONNECTED,false);
      rag.addNode(dstNode);
      for(RagNode n:rag.getNodeMap().get(dstNodeName).values()){
        if(!n.equals(dstNode))
          rag.addEdge(dstNode,n,false);
      }
      rag.setDstNode(dstNode);
    }
  }
}
