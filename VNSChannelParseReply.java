package cmx;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.bind.DatatypeConverter;

import org.apache.commons.codec.digest.DigestUtils;

import com.ibm.broker.javacompute.MbJavaComputeNode;
import com.ibm.broker.plugin.MbElement;
import com.ibm.broker.plugin.MbException;
import com.ibm.broker.plugin.MbMessage;
import com.ibm.broker.plugin.MbMQMD;
import com.ibm.broker.plugin.MbRFH2C;
import com.ibm.broker.plugin.MbMessageAssembly;
import com.ibm.broker.plugin.MbOutputTerminal;
import com.ibm.broker.plugin.MbUserException;
import com.ibm.broker.plugin.MbXMLNSC;
import com.sun.media.sound.InvalidFormatException;


public class VNSChannelParseReply extends MbJavaComputeNode {

	public void evaluate(MbMessageAssembly inAssembly) throws MbException {
		MbOutputTerminal out = getOutputTerminal("out");
		MbOutputTerminal alt = getOutputTerminal("alternate");
		MbMessage inMessage = inAssembly.getMessage();
		MbMessage env = inAssembly.getGlobalEnvironment();
		MbMessageAssembly outAssembly = null;
		MbMessage outMessage = null;
		try {
			MbElement inRoot = inMessage.getRootElement();
			String certResult = (String)inRoot.evaluateXPath("string(//*[local-name()='certResult'])");
			int trustCoreValidationResult = 0;
			String signatureResult = (String)inRoot.evaluateXPath("string(//*[local-name()='signatureResult'])");
			if (!certResult.equalsIgnoreCase("true")) {
				trustCoreValidationResult = 1;
			}
			if (!signatureResult.equalsIgnoreCase("true")) {
				trustCoreValidationResult = 1;
			}
			if (env == null) {
				throw new Exception("Global Environment is empty");
			}
			String base64data 	= (String)env.evaluateXPath("string(//*[local-name()='data'])");
			String orid 		= (String)env.evaluateXPath("string(//*[local-name()='orid'])");
			String usid 		= (String)env.evaluateXPath("string(//*[local-name()='usid'])");
			String fileName 	= (String)env.evaluateXPath("string(//*[local-name()='fileName'])");
			String chanelId 	= (String)env.evaluateXPath("string(//*[local-name()='channelId'])");
			String fileExtention = (String)env.evaluateXPath("string(//*[local-name()='fileExtension'])");
			String filePassword = (String)env.evaluateXPath("string(//*[local-name()='filePassword'])");
			String csum 		= (String)env.evaluateXPath("string(//*[local-name()='csum'])");
			MbElement inMsgId 	= env.getRootElement().getFirstElementByPath("/Headers/MsgId");
			byte [] msgId 		= (byte[])(inMsgId.getValue()); 
			ExcelFile excelFile = null;
			byte[] document 	= DatatypeConverter.parseBase64Binary(base64data);
			String fileHash 	= null; 
			if (fileExtention.equalsIgnoreCase("zip")) {
				ZipInputStream zipStream = new ZipInputStream(new ByteArrayInputStream(document));
				ZipInputStream hashStream = new ZipInputStream(new ByteArrayInputStream(document));
				ZipEntry entry = null;
				try {
					while ((entry = zipStream.getNextEntry()) != null) {
						// FileInputStream in = new FileInputStream(entryName);
						excelFile = new ExcelFile(zipStream, fileExtention, filePassword);
						break;
						//in.close();
					}
					zipStream.close();
					entry = null;
					while ((entry = hashStream.getNextEntry()) != null) {
						fileHash = calculateFileHash(hashStream);
						break;
					}
					hashStream.close();
				} catch(IOException ioe) {
					throw new MbUserException(this, "evaluate()", "ZipProcessingError", "", ioe.toString(), null);
				}
			} else if (fileExtention.equalsIgnoreCase("xls")) {
				InputStream inputDocumentStream = new ByteArrayInputStream(document);
				fileHash = calculateFileHash(document);
				excelFile = new ExcelFile(inputDocumentStream, fileExtention, filePassword);
			} else if (fileExtention.equalsIgnoreCase("xlsx")) {
				fileHash = calculateFileHash(document);
				try {
					InputStream inputDocumentStream = new ByteArrayInputStream(document);
					excelFile = new ExcelFile(inputDocumentStream, fileExtention, filePassword);
				} catch (Exception exp) {
					// Если произошла ошибка и был задан пароль, пробуем ещё раз обработать xlsx-файл без пароля
					if (filePassword != null && !filePassword.isEmpty()) {
						try {
							InputStream inputDocumentStream = new ByteArrayInputStream(document);
							excelFile = new ExcelFile(inputDocumentStream, fileExtention, "");							
						} catch (Exception exp2) {
							// Если опять произошла ошибка, эскалируем первоначальную ошибку
							throw exp;
						}
					}
				}				
			}
			
			if (excelFile == null) {
				throw new InvalidFormatException("Неверный формат файла: " + fileName + ". Тип файла: " + fileExtention + ".");
			}
					
			OrganizationInfo organization = excelFile.getOrganization();
	        ArrayList<ClientInfo> clients = excelFile.getClients();
	        
	        outMessage = new MbMessage();
	        MbElement outRoot = outMessage.getRootElement();
	        MbElement mqmd = outRoot.createElementAsLastChild(MbMQMD.PARSER_NAME);
	        mqmd.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "CodedCharSetId", 1208);
	        mqmd.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "Format", "MQHRF2  ");
	        mqmd.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "MsgId", msgId);
	        MbElement rfh2 = outRoot.createElementAsLastChild(MbRFH2C.PARSER_NAME);
	        /*  MbRFH2C.PARSER_NAME and MbXMLNSC.PARSER_NAME constants have obviously wrong values. 
	         * "Format" field of headers must contain exactly 8 characters. Simple way out is to add spaces in the tail:
	         */
	        rfh2.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "Format", "MQSTR   ");
	        rfh2.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "CodedCharSetId", 1208);
	        rfh2.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "Encoding", 546);
	        rfh2.createElementAsLastChild(MbElement.TYPE_NAME_VALUE, "NameValueCCSID", 1208);
	        MbElement rf2Usr = rfh2.createElementAsLastChild(MbElement.TYPE_NAME, "usr", null);
	        rf2Usr.createElementAsLastChild(MbElement.TYPE_NAME, "orid", orid);
	        rf2Usr.createElementAsLastChild(MbElement.TYPE_NAME, "usid", usid);
	        if (csum == null || csum.equals(""))
	        	rf2Usr.createElementAsLastChild(MbElement.TYPE_NAME, "fileHash", fileHash);
	        else
	        	rf2Usr.createElementAsLastChild(MbElement.TYPE_NAME, "fileHash", csum);
	        rf2Usr.createElementAsLastChild(MbElement.TYPE_NAME, "fileName", fileName);
	        rf2Usr.createElementAsLastChild(MbElement.TYPE_NAME, "channelId", chanelId);
	        rf2Usr.createElementAsLastChild(MbElement.TYPE_NAME, "type", excelFile.getType());
	        
	        MbElement outBody = outRoot.createElementAsLastChild(MbXMLNSC.PARSER_NAME);
	        MbElement rootBodyElement  = outBody.createElementAsLastChild(MbXMLNSC.FOLDER, "FeeRegisters", null);
	        rootBodyElement.createElementAsLastChild(MbXMLNSC.FIELD, "FormationDate" ,organization.getFileFormationDate());
	        rootBodyElement.createElementAsLastChild(MbXMLNSC.FIELD, "ContractNumber" ,organization.getContractNumber());
	        MbElement companyCard = rootBodyElement.createElementAsLastChild(MbXMLNSC.FOLDER, "CompanyCard", null);
	        companyCard.createElementAsLastChild(MbXMLNSC.FIELD, "CompanyId", organization.getCompanyId());
	        companyCard.createElementAsLastChild(MbXMLNSC.FIELD, "name", organization.getCompanyName());
	        companyCard.createElementAsLastChild(MbXMLNSC.FIELD, "INN", organization.getCompanyINN());
	        companyCard.createElementAsLastChild(MbXMLNSC.FIELD, "RS", organization.getAccoumtNumber());
	        companyCard.createElementAsLastChild(MbXMLNSC.FIELD, "BIK", organization.getBIK());
	        companyCard.createElementAsLastChild(MbXMLNSC.FIELD, "currency", organization.getCurrency());
	        if (organization.getAccrualPeriod() != null)
	        	companyCard.createElementAsLastChild(MbXMLNSC.FIELD, "accrualPeriod", organization.getAccrualPeriod());
	       
	        MbElement clientList = rootBodyElement.createElementAsLastChild(MbXMLNSC.FOLDER, "ClientsList", null);
	        int counter = 0;
	        int totalS = 0;
	        for (ClientInfo cl: clients) {
	        	counter++;
	        	MbElement client = clientList.createElementAsLastChild(MbXMLNSC.FOLDER, "Client", null);		        	
	        	client.createElementAsLastChild(MbXMLNSC.FIELD, "Number", counter);
	        	if (cl.getClientId() != null && !cl.getClientId().equals("")) {
	        		client.createElementAsLastChild(MbXMLNSC.FIELD, "Id", cl.getClientId());
	        	}
	        	if (cl.getFamilyName() != null && !cl.getFamilyName().equals("")) {
	        		client.createElementAsLastChild(MbXMLNSC.FIELD, "FamilyName", cl.getFamilyName());
	        	}
	        	if (cl.getName() != null && !cl.getName().equals("")) {
	        		client.createElementAsLastChild(MbXMLNSC.FIELD, "Name", cl.getName());
	        	}
	        	if (cl.getPatronymic() != null && !cl.getPatronymic().equals("")) {
	        		client.createElementAsLastChild(MbXMLNSC.FIELD, "Patronymic", cl.getPatronymic());
	        	}
	        	client.createElementAsLastChild(MbXMLNSC.FIELD, "Account", cl.getAccount()); 
	        	client.createElementAsLastChild(MbXMLNSC.FIELD, "sum", String.valueOf(cl.getSum()));
	        	if (cl.getBik() != null && !cl.getBik().equals("") && !OrganizationInfo.isBikOfAlfaBank(cl.getBik())) {
	        		client.createElementAsLastChild(MbXMLNSC.FIELD, "bik", cl.getBik());
	        	}
	        	client.createElementAsLastChild(MbXMLNSC.FIELD, "sts", trustCoreValidationResult);
	        	totalS += cl.getSum();
	        }
	        rootBodyElement.createElementAsLastChild(MbXMLNSC.FIELD, "EnrollmentType", organization.getBasisCode());
	        MbElement checksum = rootBodyElement.createElementAsLastChild(MbXMLNSC.FOLDER, "CheckSum", null);
	        checksum.createElementAsLastChild(MbXMLNSC.FIELD, "TotalRecords", counter);
	        checksum.createElementAsLastChild(MbXMLNSC.FIELD, "TotalSum", String.valueOf(totalS));
	        
	        outAssembly = new MbMessageAssembly(inAssembly, outMessage);
	        out.propagate(outAssembly);
	        env = null;
		} catch (MbException e) {
			throw e;
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new MbUserException(this, "evaluate()", "", "", e.toString(), null);
		} finally {
			if(outMessage != null) {
				outMessage.clearMessage();
			}
		}
	}
	
	private String calculateFileHash(InputStream in) throws Exception {
	    try {
	        String hex = DigestUtils.md5Hex(in);
	        //rearrange symbols
	        StringBuilder str = new StringBuilder(hex);
	        rearrangeSymbols(str, 0, 3);
	        rearrangeSymbols(str, 1, 5);
	        rearrangeSymbols(str, 8, 26);
	        hex = str.toString();
	        return hex;
	    } finally {
	        if (in != null) {
	            try {
	                in.close();
	            } catch (IOException e) {
	            	throw new MbUserException(this, "Failed to close file", "", "", e.toString(), null);
            	}
	        }
	    }
	}
	
	private String calculateFileHash(byte[] document) throws Exception {
	    InputStream in = null;
	    try {
	        in = new ByteArrayInputStream(document);
	        String hex = DigestUtils.md5Hex(in);
	        //rearrange symbols
	        StringBuilder str = new StringBuilder(hex);
	        rearrangeSymbols(str, 0, 3);
	        rearrangeSymbols(str, 1, 5);
	        rearrangeSymbols(str, 8, 26);
	        hex = str.toString();
	        return hex;
	    } finally {
	        if (in != null) {
	            try {
	                in.close();
	            } catch (IOException e) {
	            	throw new MbUserException(this, "Failed to close file", "", "", e.toString(), null);	               
	            }
	        }
	    }
	}

	static void rearrangeSymbols(StringBuilder str, int ind1, int ind2) {
	    char tmp = str.charAt(ind2);
	    str.setCharAt(ind2, str.charAt(ind1));
	    str.setCharAt(ind1, tmp);
	}

}
