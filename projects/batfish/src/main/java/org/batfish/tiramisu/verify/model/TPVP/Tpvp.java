package org.batfish.tiramisu.verify.model.TPVP;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.batfish.tiramisu.NodeType;
import org.batfish.tiramisu.tpg.EdgeCost;
import org.batfish.multigraph.protocol;
import org.batfish.tiramisu.tpg.Tpg;
import org.batfish.tiramisu.tpg.TpgEdge;
import org.batfish.tiramisu.tpg.TpgNode;
import org.batfish.tiramisu.tpg.TpgPath;

/**
 * @Author hydra
 * @create 2023/3/27 16:40
 */
public class Tpvp {
  Tpg g;
  TpgNode s, d;
  Map<TpgNode, EdgeCost> weight;
  Map<TpgNode, TpgNode> nextHop;
  Map<TpgNode, TpgPath> bestPath;
  HashSet<TpgEdge> failedSet;
  Map<TpgNode, Boolean> hasChanged;
  public Tpvp(Tpg graph) {
    g = graph;
    weight = new HashMap<TpgNode, EdgeCost>();
    nextHop = new HashMap<TpgNode, TpgNode>();
    bestPath = new HashMap<TpgNode, TpgPath>();
    hasChanged = new HashMap<TpgNode, Boolean>();
    failedSet = new HashSet<>();
    for(TpgNode v : g.getNodeSet()) {
      weight.put(v, new EdgeCost());
      nextHop.put(v, null);
      bestPath.put(v, new TpgPath());
    }
  }

  public EdgeCost update(EdgeCost weight_u, EdgeCost dist, NodeType type) {
    EdgeCost ec = new EdgeCost();
    if (type == NodeType.INTF) {
      // copy edge value
      ec = weight_u.copy();
    } else if (type == NodeType.OSPF) {
      // copy all variable just update ospf
      ec = weight_u.copy();
      ec.setOspf_cost(weight_u.getOspf_cost()+ dist.getOspf_cost());
      ec.setAD(dist.getAD());
    } else if (type == NodeType.BGP) {
      // update MED, LP as edge value, update AS
      ec = dist.copy();
      ec.setAs_length(weight_u.getAs_length() + dist.getAs_length());
      ec.setLp(dist.getLp());
      ec.setMed(dist.getMed());
      ec.setAD(dist.getAD());
    } else if (type == NodeType.SWITCH) {
      // copy edge value
      ec = weight_u.copy();
    } else if (type == NodeType.STAT) {
      // nothing really
      ec = weight_u.copy();
      ec.setAD(dist.getAD());
    } else if (type == NodeType.SRC) {
      // compare other vertices
      ec = weight_u.copy();
    } else if (type == NodeType.DST) {
      // just use edge
      ec = weight_u.copy();
      //ec.AD = dist.AD;
      //ec.AD = Edge.protocol_map.get(protocol.DST);
    } else if (type == NodeType.IBGP) {
      // update MED, LP as edge value, update AS
      ec = dist.copy();
      ec.setAs_length(weight_u.getAs_length());
      //ec.AD = dist.AD;
    } else if (type == NodeType.RIB) {
      // update MED, LP as edge value, update AS
      ec = weight_u.copy();
      //ec.AD = dist.AD;
    }
    return ec;
  }
  public Boolean compare(EdgeCost x, EdgeCost y) {
    // lp1 > lp2: static > ospf > BGP ; as1 < as2
    if (x.getAD() < y.getAD()) {
      return true;
    } else if (x.getAD() > y.getAD()) {
      return false;
    } else {
      // if it is BGP
      if ( (x.getAD() == EdgeCost.protocol_map.get(protocol.BGP) && y.getAD() == EdgeCost.protocol_map.get(protocol.BGP)) ||
          (x.getAD() == EdgeCost.protocol_map.get(protocol.IBGP) && y.getAD() == EdgeCost.protocol_map.get(protocol.IBGP)) ){

        if (x.getLp() > y.getLp()) {
          return true;
        } else if (x.getLp() < y.getLp()) {
          return false;
        } else {
          if (x.getAs_length() < y.getAs_length()) {
            return true;
          } else if (x.getAs_length() > y.getAs_length()) {
            return false;
          } else {
            if (x.getMed() < y.getMed()) {
              return true;
            } else if (x.getMed() > y.getMed()) {
              return false;
            }
            return false;
          }
        }
      } else if (x.getAD() == EdgeCost.protocol_map.get(protocol.OSPF) && y.getAD() == EdgeCost.protocol_map.get(protocol.OSPF)) { // if it is ospf
        if (x.getOspf_cost() < y.getOspf_cost()) {
          return true;
        } else if (x.getOspf_cost() > y.getOspf_cost()) {
          return false;
        } else {
          return false;
        }
      } else if ( (x.getAD() == EdgeCost.protocol_map.get(protocol.INTF) && y.getAD() == EdgeCost.protocol_map.get(protocol.INTF)) ||
          (x.getAD() == EdgeCost.protocol_map.get(protocol.SWITCH) && y.getAD() == EdgeCost.protocol_map.get(protocol.SWITCH)) ) { // if it is ospf
        if (x.getLp() > y.getLp()) {
          return true;
        } else if (x.getLp() < y.getLp()) {
          return false;
        } else {
          if (x.getAs_length() < y.getAs_length()) {
            return true;
          } else if (x.getAs_length() > y.getAs_length()) {
            return false;
          } else {
            if (x.getMed() < y.getMed()) {
              return true;
            } else if (x.getMed() > y.getMed()) {
              return false;
            } else {
              if (x.getOspf_cost() < y.getOspf_cost()) {
                return true;
              } else if (x.getOspf_cost() > y.getOspf_cost()) {
                return false;
              } else {
                return false;
              }
            }
          }
        }

      }
    }
    //System.out.println("Same WEIGHT");
    return false;
  }

  // return shortest path
  public TpgPath shortest(TpgNode src,TpgNode dst) {
    //System.out.println(((Node)src).getId());
    //System.out.println(currWeight);
    for(TpgNode v : g.getNodeSet()) {
      hasChanged.put(v, false);
    }

    EdgeCost currWeight;
    weight.put(dst, new EdgeCost());
    weight.get(dst).setAD(EdgeCost.protocol_map.get(protocol.DST));
    nextHop.put(dst, dst);
    bestPath.get(dst).add(dst);
    hasChanged.put(dst, true);
    // Step 2: Relax all edges |V| - 1 times. A simple
    // shortest path from src to any other Node can
    // have at-most |V| - 1 edges
    //System.out.println("Starting weight calc");
    boolean changed = true;
    //for(Node vertices : g.getVertices()) {
    boolean firstIteration = true;
    while (changed) {
      changed = false;
      for(TpgNode u : g.selectNodes(null,null,null,null)) {
        boolean curChanged = false;
        for(TpgEdge e1 : u.getOutEdges()) {
          TpgNode v = e1.getDst();
          if (!hasChanged.get(v)) {
            continue;
          }
          EdgeCost dist = e1.getEdgeCost();
          EdgeCost weight_u = weight.get(u);
          EdgeCost weight_v = weight.get(v);
          TpgPath path_v = bestPath.get(v);

          currWeight = update(weight_v, dist, e1.getType());
          //System.out.println(u + "\t" + v + "\t" + currWeight);
                  /*if (u.getId().equals("c-BGP")){
                      System.out.println(u + "\t" + currWeight+ "\t" + weight_u);
                      System.out.println(e1 + "  " + weight_v);
                  }*/

          if (compare(currWeight, weight_u) && !path_v.contains(u)) {
            //System.out.println(u + "\t" + v + "\t" + currWeight);
            weight.put(u, currWeight);
            nextHop.put(u, v);
            TpgPath path_u = new TpgPath(path_v);
            path_u.add(u);
            bestPath.put(u, path_u);
            changed = true;
            curChanged = true;
            hasChanged.put(u, true);
          }
        }
        if (!curChanged && !firstIteration) {
          hasChanged.put(u, false);
        }
      } //break;
      firstIteration = false;
    }
    return bestPath.get(src);
    //System.out.println(bestPath.get(s));
        /*
        if (weight.get(s).valid) {
            return bestPath.get(s);
        }
        return null;
        //*/
  }

}
