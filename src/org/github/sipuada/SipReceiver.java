package org.github.sipuada;

import android.javax.sip.DialogTerminatedEvent;
import android.javax.sip.IOExceptionEvent;
import android.javax.sip.RequestEvent;
import android.javax.sip.ResponseEvent;
import android.javax.sip.SipListener;
import android.javax.sip.TimeoutEvent;
import android.javax.sip.TransactionTerminatedEvent;

public class SipReceiver implements SipListener {

	@Override
	public void processRequest(RequestEvent requestEvent) {
		
	}

	@Override
	public void processResponse(ResponseEvent responseEvent) {
		
	}

	@Override
	public void processTimeout(TimeoutEvent timeoutEvent) {
		
	}

	@Override
	public void processIOException(IOExceptionEvent exceptionEvent) {
		
	}

	@Override
	public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
		
	}

	@Override
	public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
		
	}

}
