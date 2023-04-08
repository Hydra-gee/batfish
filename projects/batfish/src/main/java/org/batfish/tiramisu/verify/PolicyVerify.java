package org.batfish.tiramisu.verify;

import org.batfish.tiramisu.tpg.Tpg;
import org.batfish.tiramisu.tpg.TpgPath;
import org.batfish.tiramisu.verify.model.ILP.ilpMinCut;
import org.batfish.tiramisu.verify.model.ILP.ilpPathLength;
import org.batfish.tiramisu.verify.model.TDFS.TDFS;
import org.batfish.tiramisu.verify.model.TPVP.Tpvp;
import org.batfish.tiramisu.verify.model.TPVP.Yen;

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
        int i=1;
        for(TpgPath p:new Yen(g).ksp(g.getSrcNode(),g.getDstNode(),4)){
          System.out.println("k="+i+"path="+p);
          i+=1;
        }
        break;
    case "short":
        System.out.println(new Tpvp(g).shortest(g.getSrcNode(),g.getDstNode()));
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
