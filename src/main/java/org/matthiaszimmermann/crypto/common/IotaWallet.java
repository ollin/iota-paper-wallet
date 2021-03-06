package org.matthiaszimmermann.crypto.common;

import java.io.File;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

public class IotaWallet extends Wallet {

	public static final String JSON_VERSION = "version";
	public static final String JSON_VERSION_VALUE = "1.0";

	public static final String JSON_TECHNOLOGY = "technology";
	public static final String JSON_NETWORK = "network";

	public static final String JSON_MNEMONIC = "mnemonic";
	public static final String JSON_SEED = "seed";
	public static final String JSON_IV = "iv";
	public static final String JSON_ENCRYPTED = "encrypted";
	public static final String JSON_ADDRESS = "address";

	public IotaWallet(File file, String passPhrase) throws Exception {
		super(file, passPhrase);
	}
	
	public IotaWallet(List<String> mnemonicWords, String passPhrase, Network network) {
		this(mnemonicWords, passPhrase, ProtocolFactory.getInstance(Technology.Iota, network));
	}

	protected IotaWallet(List<String> mnemonicWords, String passPhase, Protocol protocol) {
		super(mnemonicWords, passPhase, protocol);
	}

	@Override
	public JSONObject toJson() {
		try {
			JSONObject obj = new JSONObject();

			// add version info
			obj.put(JSON_VERSION, JSON_VERSION_VALUE);

			// add seed info
			if(getAccount() != null) {
				Account account = getAccount();
				Protocol protocol = account.getProtocol();
				Network network = protocol.getNetwork();
				String mnemonic = Mnemonic.convert(getMnemonicWords());
				String seed = account.getSecret();
				String passPhrase = getPassPhrase();
				boolean encrypted = false;

				obj.put(JSON_TECHNOLOGY, protocol.getTechnology());
				obj.put(JSON_NETWORK, network.name());

				if(passPhrase != null && passPhrase.length() > 0) {
					try {
						AesUtility aes = new AesUtility(passPhrase);
						encrypted = true;
						
						obj.put(JSON_MNEMONIC, aes.encrypt(mnemonic));
						obj.put(JSON_SEED, aes.encrypt(seed));
						obj.put(JSON_IV, aes.getIv());
					}
					catch (Exception e) {
						new RuntimeException(e.getMessage());
					}
				}
				else {
					obj.put(JSON_MNEMONIC, mnemonic);
					obj.put(JSON_SEED, seed);
				}

				// add encrypted info
				obj.put(JSON_ENCRYPTED, encrypted);

				// add address info
				obj.put(JSON_ADDRESS, account.getAddress());
			}

			return obj;
		}
		catch(JSONException ex) {
			throw new RuntimeException(ex);
		}
	}

	@Override
	protected void restoreWallet(String jsonString, String passPhrase) throws Exception {
		if(jsonString.isEmpty()) {
			throw new JSONException("Empty wallet file");
		}

		// convert wallet file string to json object
		JSONObject node = new JSONObject(jsonString);

		// check and extract version
		if(!node.has(JSON_VERSION)) {
			throw new JSONException("Wallet file has no version attribute");
		}
		
		if(!JSON_VERSION_VALUE.equals(node.getString(JSON_VERSION))) {
			throw new JSONException("Wallet file has unkonwn version attribute. Expected value: " + JSON_VERSION_VALUE);
		}
		
		// check and extract protocol
		if(!node.has(JSON_TECHNOLOGY)) {
			throw new JSONException("Wallet file has no technology attribute");
		}
		
		if(!Technology.Iota.name().equals(node.getString(JSON_TECHNOLOGY))) {
			throw new JSONException("Wallet file has unexpected technology attribute. Expected value: " + Technology.Iota.name());
		}
		
		if(!node.has(JSON_NETWORK)) {
			throw new JSONException("Wallet file has no network attribute");
		}
				
		// check and extract seed
		if(!node.has(JSON_SEED)) {
			throw new JSONException("Wallet file has no seed attribute");
		}

		// check and extract mnemonic
		if(!node.has(JSON_MNEMONIC)) {
			throw new JSONException("Wallet file has no mnemonic attribute");
		}

		if(!node.has(JSON_ADDRESS)) {
			throw new JSONException("Wallet file has no address attribute");
		}

		if(!node.has(JSON_ENCRYPTED)) {
			throw new JSONException("Wallet file has no encrypted attribute");
		}
		
		String address = node.getString(JSON_ADDRESS);
		List<String> mnemonic = null;
		String seed = null;
		
		// get mnemonic and seed
		if(node.getBoolean(JSON_ENCRYPTED)) {
			if(!node.has(JSON_IV)) {
				throw new JSONException("Wallet file has no iv attribute (required for encrypted seed)");
			}
			
			AesUtility aes = new AesUtility(passPhrase);
			String iv = node.getString(JSON_IV);
			
			mnemonic = Mnemonic.convert(aes.decrypt(node.getString(JSON_MNEMONIC), iv));
			seed = aes.decrypt(node.getString(JSON_SEED), iv);
		}
		else {
			mnemonic = Mnemonic.convert(node.getString(JSON_MNEMONIC));
			seed = node.getString(JSON_SEED);
		}
		
		System.out.println("SYSOUT seed: " + seed);
		System.out.println("SYSOUT mnemonic: " + mnemonic);
		
		Network network = Network.get(node.getString(JSON_NETWORK));
		Protocol protocol = ProtocolFactory.getInstance(Technology.Iota, network);
		Account walletAccount = protocol.restoreAccount(mnemonic, passPhrase);
		
		if(!walletAccount.getAddress().equals(address)) {
			throw new JSONException("Wallet address mismatch. Expected value" + walletAccount.getAddress());
		}
		
		if(!walletAccount.getSecret().equals(seed)) {
			throw new JSONException("Wallet seed mismatch. Expected value" + walletAccount.getSecret());
		}
	}
}
