package org.batfish.tiramisu.tpg;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.batfish.multigraph.protocol;
import org.batfish.symbolic.Protocol;
import org.batfish.tiramisu.NodeType;
import org.batfish.tiramisu.rag.RagEdge;

@Data
public class TpgNode {
    private String deviceId;
    private Protocol protocol;
    private boolean taint;
    private NodeType vlanType;
    private String vlanPeerId;
    private List<TpgEdge> outEdges = new ArrayList<>();
    public TpgNode(String deviceId,Protocol protocol){
        this.deviceId = deviceId;
        this.protocol = protocol;
        this.vlanType = NodeType.NONE;
    }
    public TpgNode(String deviceId,Protocol protocol,NodeType vlanType,String peerId){
        this.deviceId = deviceId;
        this.protocol = protocol;
        this.vlanType = vlanType;
        this.vlanPeerId = peerId;
    }

    public boolean compareTo(TpgNode node2){
        if(this.deviceId.equals(node2.getDeviceId()) || node2.getDeviceId() == null){
            if(this.protocol.equals(node2.getProtocol()) || node2.getProtocol()==null){
                if(this.vlanType.equals(node2.getVlanType()) || node2.getVlanType()==null){
                    if(this.vlanPeerId.equals(node2.getVlanPeerId()) || node2.getVlanPeerId()==null){
                        return true;
                    }
                }
            }
        }
        return false;
    }
}