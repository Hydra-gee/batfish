package org.batfish.tiramisu.verify;

import org.batfish.tiramisu.tpg.Tpg;
import org.batfish.tiramisu.verify.model.ILP.ilpMinCut;
import org.batfish.tiramisu.verify.model.ILP.ilpPathLength;
import org.batfish.tiramisu.verify.model.TDFS.TDFS;
import org.batfish.tiramisu.verify.model.TPVP.Tpvp;

/**
 * @Author hydra
 * @create 2023/3/22 13:31
 */
public class PolicyVerify {
  Tpg g;
  String wp;
  public PolicyVerify(Tpg g){
    this.g = g;
  }
  public void verify(String policy){
    boolean result = false;
    switch (policy){
      case "block":
        result = new TDFS(g).alwaysBlocked(g.getSrcNode(),g.getDstNode());
        System.out.println("ALWAYS BLOCK:"+result);
        break;
      case "waypoint":
        result = new TDFS(g).isWaypoint(g.getSrcNode(),g.getDstNode(),wp);
        System.out.println("WAY POINT:"+result);
        break;
      case "pref":
        break;
      case "short":
        new Tpvp(g).shortest();
        break;
      case "kfail":
        new ilpMinCut(g).fail();
        break;
      case "bound":
        new ilpPathLength(g).boundLength();
      break;
      case "equal":
        new ilpPathLength(g).equalLength();
      break;
      default:
        System.out.println("NO POLICY FOUND");
        break;
    }
  }
}
