package org.batfish.tiramisu.tpg;

import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Data;
import org.batfish.symbolic.Protocol;
import org.batfish.tiramisu.NodeType;

@Data
public class TpgNode {
    private String deviceId;
    private Protocol protocol;
    private boolean taint;
    private NodeType vlanType;
    private String vlanPeerId;
    private boolean valid;
    private Set<TpgEdge> outEdges = new LinkedHashSet<>();
    private Set<TpgEdge> inEdges = new LinkedHashSet<>();
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
        if(node2.getDeviceId() == null || this.deviceId.equals(node2.getDeviceId())){
            if( node2.getProtocol()==null || this.protocol.equals(node2.getProtocol())){
                if(node2.getVlanType()==null || this.vlanType.equals(node2.getVlanType())){
                    if(node2.getVlanPeerId()==null || this.vlanPeerId.equals(node2.getVlanPeerId())){
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override public String toString(){
        return deviceId + " " + protocol + " " + vlanType + " " + vlanPeerId;
    }

    public void print(){
        String output = deviceId + " " + protocol + " " + vlanType + " " + vlanPeerId;
        System.out.println(output);
    }

    @Override
    public int hashCode(){
        String output = deviceId + " " + protocol + " " + vlanType + " " + vlanPeerId;
        return output.hashCode();
    }

    @Override
    public boolean equals(Object o){
        if(!(o instanceof TpgNode))
        {
            return false;
        }
        return this.hashCode() == o.hashCode();
    }
}