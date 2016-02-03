package org.github.sipuada;

import android.javax.sip.ServerTransaction;

public interface IIncomingRequestsListener {
	/**
	 * Called when a SIP INVITE request is received, serverTransaction should be kept for the application.
	 * 
	 * @return should return false whether the application is Busy.
	 */
	boolean onInviteRequest(ServerTransaction serverTransaction);

	/**
	 * Called when a SIP CANCEL request is received.
	 * @return should return a ServerTransaction whether find a matching transaction and the transaction is not Completed.
	 */
	ServerTransaction onCancelRequest(ServerTransaction incomingServerTransaction); 

	/**
	 * Called when a SIP BYE request is received. The application should stop
	 * sending and listening for media, the only case where it can elect not to
	 * are multicast sessions.
	 */
	void onByeRequest();
}
