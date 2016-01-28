package org.github.sipuada.requester;

import java.util.Properties;
import java.util.TooManyListenersException;

import org.github.sipuada.SipReceiver;

import android.javax.sip.ClientTransaction;
import android.javax.sip.Dialog;
import android.javax.sip.InvalidArgumentException;
import android.javax.sip.ListeningPoint;
import android.javax.sip.ObjectInUseException;
import android.javax.sip.ServerTransaction;
import android.javax.sip.SipFactory;
import android.javax.sip.SipProvider;
import android.javax.sip.SipStack;
import android.javax.sip.TransportNotSupportedException;
import android.javax.sip.header.CallIdHeader;

public class SipConnection {

	private String sipStackName;
	private SipStack sipStack;
	private String localIpAddress;
	private int localSipPort;
	private String transport;
	private SipReceiver sipReceiver;
	private SipProvider provider;
	private ListeningPoint listeningPoint;
	private ServerTransaction serverTransaction;
	private ClientTransaction clientTransaction;
	private CallIdHeader currentCallId;
	private Dialog currentDialog;
	private long callSequence = 1L;

	private static final int MIN_PORT = 5000, MAX_PORT = 6000;

	public SipConnection(final String sipStackName, final String localIpAddress, final int localSipPort,
			final String transport, final SipReceiver sipReceiver) throws Exception {

		if (null != sipStackName || sipStackName.trim().length() == 0) {
			throw new Exception("Invalid name for sip stack.");
		}
		this.sipStackName = sipStackName;
		this.localIpAddress = localIpAddress;
		this.localSipPort = localSipPort;
		this.transport = transport;
		this.sipReceiver = sipReceiver;
		// Create SipFactory
		SipFactory sipFactory = SipFactory.getInstance();

		// Create unique name properties for SipStack
		Properties properties = new Properties();
		properties.setProperty("android.javax.sip.STACK_NAME", sipStackName);

		// Create SipStack object
		sipStack = sipFactory.createSipStack(properties);

		initialize();
	}

	private void initialize() {
		boolean successfullyBound = false;
		int tries = 50;
		ListeningPoint listeningPoint = null;

		/*
		 * TODO: reparar esse trecho de codigo. Ele trava a UI. TODO: ajustar
		 * numero de tentativas. Antes ele tentava infinitamente.
		 */
		while (tries > 0 && !successfullyBound) {
			try {
				listeningPoint = sipStack.createListeningPoint(localIpAddress, localSipPort, transport);
				this.listeningPoint = listeningPoint;
			} catch (InvalidArgumentException ex) {
				ex.printStackTrace();
				System.err.println("InvalidArgumentException - listening point already exists...");
				// choose another port between MIN and MAX
				localSipPort = (int) ((MAX_PORT - MIN_PORT) * Math.random()) + MIN_PORT;
				tries--;
				continue;
			} catch (TransportNotSupportedException e) {
				e.printStackTrace();
			}
			successfullyBound = true;
			// TODO Is an update of localSipPort really required?
			// virtualSipAlarmStation.getSipProfile().setLocalSipPort(localSipPort);
			// TODO: verify if localSipPort in sipProfile of
			// sipAlarmStationMessageHandlerPassport is up to date.
			// TODO: needs to check for the public port again if the local one
			// changed
		}

		try {
			sipStack.createSipProvider(this.listeningPoint);
			provider.addSipListener(sipReceiver);
		} catch (TooManyListenersException e1) {
			e1.printStackTrace();
		} catch (ObjectInUseException e2) {
			System.err.println("Provider already attached!");
			e2.printStackTrace();
		}

	}

	public SipStack getSipStack() {
		return sipStack;
	}

	public void setSipStack(SipStack sipStack) {
		this.sipStack = sipStack;
	}

	public String getLocalIpAddress() {
		return localIpAddress;
	}

	public void setLocalIpAddress(String localIpAddress) {
		this.localIpAddress = localIpAddress;
	}

	public int getLocalSipPort() {
		return localSipPort;
	}

	public void setLocalSipPort(int localSipPort) {
		this.localSipPort = localSipPort;
	}

	public SipProvider getProvider() {
		return provider;
	}

	public void setProvider(SipProvider provider) {
		this.provider = provider;
	}

	public ListeningPoint getListeningPoint() {
		return listeningPoint;
	}

	public void setListeningPoint(ListeningPoint listeningPoint) {
		this.listeningPoint = listeningPoint;
	}

	public ServerTransaction getServerTransaction() {
		return serverTransaction;
	}

	public void setServerTransaction(ServerTransaction serverTransaction) {
		this.serverTransaction = serverTransaction;
	}

	public ClientTransaction getClientTransaction() {
		return clientTransaction;
	}

	public void setClientTransaction(ClientTransaction clientTransaction) {
		this.clientTransaction = clientTransaction;
	}

	public CallIdHeader getCurrentCallId() {
		return currentCallId;
	}

	public void setCurrentCallId(CallIdHeader currentCallId) {
		this.currentCallId = currentCallId;
	}

	public Dialog getCurrentDialog() {
		return currentDialog;
	}

	public void setCurrentDialog(Dialog currentDialog) {
		this.currentDialog = currentDialog;
	}

	public long getCallSequence() {
		return callSequence;
	}

	public void setCallSequence(long callSequence) {
		this.callSequence = callSequence;
	}

	public long incrementCallSequence() {
		return callSequence++;
	}

}
