package org.github.sipuadaui;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;

import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
callId
import org.github.sipuada.Sipuada;
import org.github.sipuada.SipuadaApi.CallInvitationCallback;
import org.github.sipuada.SipuadaApi.RegistrationCallback;
import org.github.sipuada.SipuadaApi.SipuadaListener;

public class SIPClientMain implements SipuadaListener {

	private JFrame frmSipuada;
	private JTextField registrarDomainTextField;
	private JTextField registrarUserNameTextField;
	private JTextField callerDomainTextField;
	private JTextField callerUserTextField;
	private JPasswordField passwordField;
	private JTextArea textArea;
	private Sipuada sipuada;
	private String currentCallID;
	private JButton btAcceptCall;
	private JButton btRejectCall;
	private boolean isBusy = false;
	private JButton btnCancel;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					SIPClientMain window = new SIPClientMain();
					window.frmSipuada.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public SIPClientMain() {
		initialize();
		setDefautValues();
	}

	private void setDefautValues() {
		registrarDomainTextField.setText("192.168.130.207:5060");
		registrarUserNameTextField.setText("renan");
		passwordField.setText("renan");
	}

	private void setUPCallButton(JButton callButton) {
		
		callButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if(sipuada == null){
					textArea.setText(textArea.getText()
							+ System.getProperty("line.separator")
							+ " - "
							+ " Required to register!");
					btnCancel.setEnabled(true);
				}
				
				sipuada.inviteToCall(callerUserTextField.getText(),
						callerDomainTextField.getText(),
						new CallInvitationCallback() {
							@Override
							public void onWaitingForCallInvitationAnswer(
									String callId) {
								textArea.setText(textArea.getText()
										+ System.getProperty("line.separator")
										+ " - "
										+ " Waiting For Call InvitationAnswer ...");
									currentCallID = callId;
							}

							@Override
							public void onCallInvitationRinging(String callId) {
								textArea.setText(textArea.getText()
										+ System.getProperty("line.separator")
										+ " - " + " Ringing ...");
								btnCancel.setEnabled(false);

							}

							@Override
							public void onCallInvitationDeclined(String reason) {
								textArea.setText(textArea.getText()
										+ System.getProperty("line.separator")
										+ " - " + "Invitation Declined.");
								btnCancel.setEnabled(false);
							}
						});
			}
		});
	}

	private void setUpRegisterButton(JButton registerButton) {

		registerButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (sipuada == null) {
					sipuada = new Sipuada(registrarUserNameTextField.getText(),
							registrarDomainTextField.getText(), passwordField
									.getName(), Util.getIPAddress(true), 55000,
							"TCP", SIPClientMain.this);
				}

				sipuada.register(new RegistrationCallback() {

					@Override
					public void onRegistrationSuccess(
							List<String> registeredContacts) {
						textArea.setText(textArea.getText()
								+ System.getProperty("line.separator") + " - "
								+ " successfully registered");
					}

					@Override
					public void onRegistrationRenewed() {
						textArea.setText(textArea.getText()
								+ System.getProperty("line.separator") + " - "
								+ " Registration Renewed");
					}

					@Override
					public void onRegistrationFailed(String reason) {
						textArea.setText(textArea.getText()
								+ System.getProperty("line.separator") + " - "
								+ " failure to register: " + reason);
					}
				});
			}
		});

	}

	private void setUpAcceptCallButton(JButton btAcceptCall) {
		btAcceptCall.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				sipuada.acceptCallInvitation(currentCallID);
			}
		});
	}

	private void setUpRejectCallButton(JButton btRejectCall) {
		btRejectCall.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				sipuada.declineCallInvitation(currentCallID);
			}
		});
	}

	private void setUpCancelButton(JButton btCancel) {
		btCancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				sipuada.cancelCallInvitation(currentCallID);
				//TODO
			}
		});
	}

	
	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frmSipuada = new JFrame();
		frmSipuada.setTitle("SIP User Agent - Sipuada");
		frmSipuada.setBounds(100, 100, 800, 600);
		frmSipuada.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		JLabel lblNewLabel = new JLabel("Domain");

		registrarDomainTextField = new JTextField();
		registrarDomainTextField.setColumns(10);

		JLabel label = new JLabel("User");

		registrarUserNameTextField = new JTextField();
		registrarUserNameTextField.setColumns(10);

		JLabel label_1 = new JLabel("Password");
		frmSipuada.getContentPane().setLayout(
				new MigLayout("", "[207px,grow][142px,grow][142px,grow][][]",
						"[15px][19px][][][][grow][][]"));
		frmSipuada.getContentPane().add(registrarUserNameTextField,
				"cell 1 1,growx,aligny top");
		frmSipuada.getContentPane().add(label_1, "cell 2 0,alignx left,aligny top");
		frmSipuada.getContentPane().add(registrarDomainTextField,
				"cell 0 1,growx,aligny top");
		frmSipuada.getContentPane().add(lblNewLabel,
				"cell 0 0,alignx left,aligny top");
		frmSipuada.getContentPane().add(label, "cell 1 0");

		passwordField = new JPasswordField();
		frmSipuada.getContentPane().add(passwordField, "cell 2 1,growx,aligny top");

		JButton btnRegister = new JButton("Register");
		setUpRegisterButton(btnRegister);
		frmSipuada.getContentPane().add(btnRegister, "cell 4 1");

		JLabel label_2 = new JLabel("Domain");
		frmSipuada.getContentPane().add(label_2, "cell 0 2");

		JLabel lblNewLabel_1 = new JLabel("User");
		frmSipuada.getContentPane().add(lblNewLabel_1, "cell 1 2");

		callerDomainTextField = new JTextField();
		frmSipuada.getContentPane().add(callerDomainTextField, "cell 0 3,growx");
		callerDomainTextField.setColumns(10);

		callerUserTextField = new JTextField();
		frmSipuada.getContentPane().add(callerUserTextField, "cell 1 3,growx");
		callerUserTextField.setColumns(10);

		JButton btnCall = new JButton("Call");
		setUPCallButton(btnCall);

		frmSipuada.getContentPane().add(btnCall, "cell 4 3");

		JLabel lblLog = new JLabel("Log");
		frmSipuada.getContentPane().add(lblLog, "cell 0 4");

		textArea = new JTextArea();
		frmSipuada.getContentPane().add(textArea, "cell 0 5 5 1,grow");

		btAcceptCall = new JButton("Accept Call");
		btAcceptCall.setEnabled(false);
		setUpAcceptCallButton(btAcceptCall);
		frmSipuada.getContentPane().add(btAcceptCall, "cell 0 7");

		btRejectCall = new JButton("Reject Call");
		btRejectCall.setEnabled(false);
		setUpRejectCallButton(btRejectCall);
		frmSipuada.getContentPane().add(btRejectCall, "cell 1 7");
		
		btnCancel = new JButton("Cancel");
		btnCancel.setEnabled(false);
		setUpCancelButton(btRejectCall);
		frmSipuada.getContentPane().add(btnCancel, "cell 2 7");
	}

	
	@Override
	public boolean onCallInvitationArrived(String callId) {
		textArea.setText(textArea.getText()
				+ System.getProperty("line.separator") + " - "
				+ " Call Invitation Arrived.");
		currentCallID = callId;
		btAcceptCall.setEnabled(true);
		btRejectCall.setEnabled(true);
		return isBusy;
	}

	@Override
	public void onCallInvitationCanceled(String reason, String callId) {
		textArea.setText(textArea.getText()
				+ System.getProperty("line.separator") + " - "
				+ " Call Invitation Canceled.");
		btAcceptCall.setEnabled(false);
		btRejectCall.setEnabled(false);
	}

	@Override
	public void onCallInvitationFailed(String reason, String callId) {
		textArea.setText(textArea.getText()
				+ System.getProperty("line.separator") + " - "
				+ " Call Invitation Failed.");

	}

	@Override
	public void onCallEstablished(String callId) {
		textArea.setText(textArea.getText()
				+ System.getProperty("line.separator") + " - "
				+ " Call Established.");

	}

	@Override
	public void onCallFinished(String callId) {
		textArea.setText(textArea.getText()
				+ System.getProperty("line.separator") + " - "
				+ " Call Finished.");
		btAcceptCall.setEnabled(false);
		btRejectCall.setEnabled(false);

	}
}
