package android.gov.nist.gnjvx.sip.stack;

import android.gov.nist.gnjvx.sip.message.SIPMessage;

public interface RawMessageChannel {
	
	public abstract void processMessage(SIPMessage sipMessage) throws Exception ;

}
