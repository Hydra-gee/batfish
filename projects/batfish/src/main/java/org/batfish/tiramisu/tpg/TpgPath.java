package org.batfish.tiramisu.tpg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;


public class TpgPath {

  ArrayList<TpgNode> pathTpgNodes;
  EdgeCost ec;
  HashSet<String> nodeNames;

  public TpgPath() {
    pathTpgNodes = new ArrayList<TpgNode>();
    ec = new EdgeCost();
    nodeNames = new HashSet<>();
  }

  public TpgPath(TpgPath prev) {
    pathTpgNodes = new ArrayList<TpgNode>();
    nodeNames = new HashSet<>();
    for (TpgNode vertex : prev.pathTpgNodes) {
      //TpgNode n = vertex.copy();
      pathTpgNodes.add(vertex);
      nodeNames.add(vertex.getDeviceId());
    }
    ec = prev.ec.copy();
  }

  public boolean contains(TpgNode node) {
    return nodeNames.contains(node.getDeviceId());
  }

  public void add(TpgNode n) {
    pathTpgNodes.add(0, n);
    nodeNames.add(n.getDeviceId());
  }

  public void addEnd(TpgNode n) {
    pathTpgNodes.add(n);
    nodeNames.add(n.getDeviceId());
  }


  public void setTpgPath() {
    Collections.reverse(pathTpgNodes);
  }

  public TpgNode getVertex(int i) {
    if (i > pathTpgNodes.size())
      return null;
    return pathTpgNodes.get(i);
  }

  public ArrayList<TpgNode> getTpgNodes() {
    return pathTpgNodes;
  }

  @Override
  public String toString() {
    return "TpgPath TpgNodes:" + pathTpgNodes;
    //return "TpgPath TpgNodes:" + pathTpgNodes;
  }

  public boolean equals(TpgPath path2) {
    if (path2 == null)
      return false;

    ArrayList<TpgNode> nodes = path2.getTpgNodes();

    int numTpgNodes1 = pathTpgNodes.size();
    int numTpgNodes2 = nodes.size();

    if (numTpgNodes1 != numTpgNodes2) {
      return false;
    }

    for (int i = 0; i < numTpgNodes1; i++) {
      TpgNode node1 = pathTpgNodes.get(i);
      TpgNode node2 = nodes.get(i);
      if (!node1.getDeviceId().equals(node2.getDeviceId()))
        return false;
    }

    return true;
  }

}