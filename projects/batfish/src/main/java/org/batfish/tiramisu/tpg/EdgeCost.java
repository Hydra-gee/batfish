package org.batfish.tiramisu.tpg;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.batfish.multigraph.protocol;

/**
 * @Author hydra
 * @create 2023/3/27 17:05
 */
@Data
@AllArgsConstructor
public class EdgeCost {
  int AD;
  int ospf_cost;
  int as_length;
  int lp;
  int med;
  int rediscost;
  int weight;

  public static final Map<protocol, Integer> protocol_map = createMap();

  static Map<protocol, Integer> createMap() {
    Map<protocol, Integer> result = new HashMap<protocol, Integer>();
    // STAT, OSPF, BGP, REDISOB, REDISBO, IBGP, DEF, NONE;
    result.put(protocol.STAT, 1);
    result.put(protocol.OSPF, 100);
    result.put(protocol.BGP, 20);
    result.put(protocol.REDISOB, 20);
    result.put(protocol.REDISBO, 100);
    result.put(protocol.IBGP, 200);
    result.put(protocol.DEF, 100);
    result.put(protocol.NONE, 100000);
    result.put(protocol.INTF, 10000);
    result.put(protocol.RIB, 1000);
    result.put(protocol.REDISSB, 20);
    result.put(protocol.REDISSO, 100);
    result.put(protocol.SWITCH, 10000);
    result.put(protocol.SRC, 0);
    result.put(protocol.DST, 0);

    return Collections.unmodifiableMap(result);
  }

  public EdgeCost(){

  }

  public EdgeCost copy(){
    return new EdgeCost(AD,ospf_cost,as_length,lp,med,rediscost,weight);
  }
}
