package local.quiettv.launcher;

/** A capability result keeps absence, optional setup and active support distinct. */
final class DeviceCapability {
    enum State { AVAILABLE, OPTIONAL, WAITING, UNSUPPORTED, INACTIVE }

    final String id;
    final State state;
    final String reason;

    private DeviceCapability(String id,State state,String reason){
        this.id=id==null?"":id;
        this.state=state;
        this.reason=reason==null?"":reason;
    }
    static DeviceCapability available(String id,String reason){return new DeviceCapability(id,State.AVAILABLE,reason);}
    static DeviceCapability optional(String id,String reason){return new DeviceCapability(id,State.OPTIONAL,reason);}
    static DeviceCapability waiting(String id,String reason){return new DeviceCapability(id,State.WAITING,reason);}
    static DeviceCapability unsupported(String id,String reason){return new DeviceCapability(id,State.UNSUPPORTED,reason);}
    static DeviceCapability inactive(String id,String reason){return new DeviceCapability(id,State.INACTIVE,reason);}
    boolean isAvailable(){return state==State.AVAILABLE;}
}
