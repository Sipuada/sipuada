package org.github.sipuada;

import java.util.HashMap;
import java.util.Map;

import android.javax.sip.Dialog;
import android.javax.sip.ServerTransaction;
import android.javax.sip.message.Response;

public class Sipuada implements IIncomingRequestsListener{

	
	
	private Map<String , ServerTransaction> serverTransactions;
	private UserAgent userAgent;
	private Sipuada.Listener listener;

	/**
	 * Application API
	 * @param username username in SIP Registrar Entity.
	 * @param domain domain of SIP Registrar Entity.
	 * @param password password in SIP Registrar Entity.
	 * @param localIp The local ip.
	 * @param localPort Local port for SIP communication.
	 * @param transport transport protocol: TCP, UDP, TLS.
	 */
	public Sipuada (String username, String domain, String password,
			String localIp, int localPort, String transport){
		userAgent = new UserAgent(username, domain, password, localIp, localPort, transport);
		serverTransactions = new HashMap<>();
	}
	
	public interface Listener {
		boolean onCallReceived(String callId);
		boolean onCancelCallRecived(String callId);
		void onCallEnded();
	}

	public void register(){
		userAgent.getUserAgentClient().sendRegisterRequest();
	}
	
	public void call(String sipAddress){
		String remoteUser = sipAddress.split("@")[0];
		String remoteDomain = sipAddress.split("@")[1];
		userAgent.getUserAgentClient().sendInviteRequest(remoteUser, remoteDomain);
	}
	
	public void acceptCall(String callId){
		ServerTransaction serverTransaction = serverTransactions.get(callId);
		userAgent.getUserAgentServer().sendResponse(Response.OK, serverTransaction);
	}
	
	public void rejectCall(String callId){
		ServerTransaction serverTransaction = serverTransactions.get(callId);
		userAgent.getUserAgentServer().sendResponse(Response.BUSY_HERE, serverTransaction);
	}
	
	public void endCall(String callId){
		ServerTransaction serverTransaction = serverTransactions.get(callId);
		//TODO get dialog
		Dialog dialog = null;
		userAgent.getUserAgentClient().sendByeRequest(dialog);
	}
	
	@Override
	public boolean onInviteRequest(ServerTransaction serverTransaction) {
		/*
		 * TODO verify if call id could be key.
		 */
		String callId = serverTransaction.getDialog().getCallId().getCallId();
		serverTransactions.put(callId, serverTransaction);
		return listener.onCallReceived(callId);
	}

	@Override
	public ServerTransaction onCancelRequest(ServerTransaction incomingServerTransaction) {
		/*
		 * TODO verify if call id could be key.
		 */
		String cancelCallId = incomingServerTransaction.getDialog().getCallId().getCallId();
		ServerTransaction canceledTransaction = serverTransactions.get(cancelCallId);
		if(canceledTransaction != null){
			 listener.onCancelCallRecived(cancelCallId);
			 return canceledTransaction;
		}
		return null;
	}

	@Override
	public void onByeRequest() {
		listener.onCallEnded();
	}
	
}
