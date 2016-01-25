package android.gov.nist.gnjvx.sip;

import android.javax.sip.ServerTransaction;


public interface ServerTransactionExt {
	/**
	 * Return the canceled Invite transaction corresponding to an
	 * incoming CANCEL server transaction.
	 * 
	 * @return -- the canceled Invite transaction.
	 * 
	 */
	public ServerTransaction getCanceledInviteTransaction();
}
