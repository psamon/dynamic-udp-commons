/**
 * 
 */
package com.anz.common.compute.impl;

import com.anz.common.compute.ComputeInfo;
import com.anz.common.compute.OutputTarget;
import com.anz.common.compute.TransformType;
import com.anz.common.error.ExceptionMessage;
import com.anz.common.error.TransformFailureResponse;
import com.anz.common.transform.ITransformer;
import com.anz.common.transform.TransformUtils;
import com.ibm.broker.config.proxy.MessageFlowProxy;
import com.ibm.broker.plugin.MbElement;
import com.ibm.broker.plugin.MbMessage;
import com.ibm.broker.plugin.MbMessageAssembly;
import com.ibm.broker.plugin.MbOutputTerminal;

/**
 * @author sanketsw & psamon
 *
 */
public abstract class CommonErrorTransformCompute extends CommonJavaCompute {

	
	/* (non-Javadoc)
	 * @see com.anz.common.compute.impl.CommonJavaCompute#execute(com.ibm.broker.plugin.MbMessageAssembly, com.ibm.broker.plugin.MbMessageAssembly)
	 */
	@SuppressWarnings("unused")
	@Override
	public void execute(MbMessageAssembly inAssembly,
			MbMessageAssembly outAssembly) throws Exception {
		try {
			MbMessage inMessage = inAssembly.getMessage();
			MbMessage outMessage = outAssembly.getMessage();
			
			/*
			 * Set the HTTPResponseHeader to Http 1.0/500 Internal Server Error
			 * Only for HTTP flows and when the error is not already set something else
			 * This block will be executed for internal errors in transformation such as NullPointerExceptions etc. 
			 */
			
			logger.info("CommonErrorTransformCompute:execute()");
			MessageFlowProxy flow = ComputeUtils.getFlowProxy("TESTNODE_root", "default", "HttpToHttp-app", "Main");
			String fireAndForget = (String) flow.getUserDefinedProperty("FIRE_AND_FORGET");
			logger.info("fireAndForget = {}", fireAndForget);
			
			TransformType transformType = getTransformationType();

			if((transformType.equals(TransformType.HTTP_HHTP) || transformType.equals(TransformType.HTTP_MQ))
					&& outMessage.getRootElement().getFirstElementByPath("HTTPResponseHeader") == null) {				
				
				if(! "true".equalsIgnoreCase(fireAndForget)) {					
					ComputeUtils.setElementInTree("application/json", outMessage, "Properties", "ContentType");
					MbElement replyStatusCode = ComputeUtils.setHttpReplyStatus(outAssembly, "500"); 
					logger.info("Setting http reply status code: {} ", replyStatusCode);
				}
			}
			
			
			String outputJson = executeTranformation(outAssembly);
			if (outputJson != null) {
				// Detach Original Exception Node from the response
				MbElement exception = outAssembly.getExceptionList().getRootElement().getFirstChild();
				if(exception != null)
					exception.detach();
				
				// Write this outputJson to outMessage
				ComputeUtils.replaceStringAsBlob(outMessage, outputJson);
			}
			
			propagateToAlertQueueIfApplicable(inAssembly, outputJson);
			
		} catch(Exception e) {
			logger.throwing(e);
			throw e;
		}
		
	}
	
	/**
	 * Propagate SplunkAlertFormat to alert queue if applicable
	 * @param outAssembly
	 * @param outputJson
	 * @throws Exception
	 */
	protected void propagateToAlertQueueIfApplicable(MbMessageAssembly inAssembly, 
			String outputJson) throws Exception {
		
		MessageFlowProxy flow = ComputeUtils.getFlowProxy("TESTNODE_root", "default", "HttpToHttp-app", "Main");
		String alert = (String) flow.getUserDefinedProperty("ALERT");
		logger.info("ALERT = {}", alert);
		
		if("true".equalsIgnoreCase(alert)) {
			
			MbMessage outMessage = new MbMessage();
			MbMessageAssembly outAssembly = new MbMessageAssembly(inAssembly, outMessage);
			
			ExceptionMessage exceptionMessage = TransformUtils.fromJSON(outputJson, ExceptionMessage.class);
			String alertOutput = TransformUtils.toSplunkAlertFormat(exceptionMessage);
			ComputeUtils.replaceStringAsBlob(outAssembly.getMessage(), alertOutput);
			
			logger.info("Output sent on Alert queue: {}", alertOutput);
			
			ComputeUtils.removeElementFromTree(outAssembly.getMessage(), "HTTPInputHeader");
			
			ComputeUtils.setElementInTree((String) getUserDefinedAttribute("ALERT_QUEUE_MGR"), outAssembly.getLocalEnvironment(), "Destination", "MQ", "DestinationData", "queueManagerName");
			ComputeUtils.setElementInTree((String) getUserDefinedAttribute("ALERT_QUEUE"), outAssembly.getLocalEnvironment(), "Destination","MQ","DestinationData", "queueName" );
			
			MbOutputTerminal alt = getOutputTerminal("alternate");
			alt.propagate(outAssembly);
		}
		
	}

	/**
	 * Write business logic here if you merely need to transform the message
	 * from JSON to JSON
	 * 
	 * @param outAssembly
	 *            output assembly copied from inAssembly
	 * @return output JSON Data to be placed in the message
	 */
	public String executeTranformation(MbMessageAssembly outAssembly) throws Exception {
		String outputString = null;
		// Remove the subflow name if any from the Transformer class before com.anz.**
		//String transformerClassName = getName().substring(getName().indexOf("com"));
		//logger.info("Creating instance of {}", transformerClassName);
		try {
			//ITransformer<MbMessageAssembly, String> jsonTransformer = (ITransformer<MbMessageAssembly, String>)Class.forName(transformerClassName).newInstance();
			ITransformer<MbMessageAssembly, String> transformer = getTransformer();
			outputString = transformer.execute(outAssembly, appLogger, metaData);
		} catch(Exception e) {
			logger.throwing(e);
			throw e;
		}
		return outputString;
	}
	
	/**
	 * Get the external transformer class instance
	 * @return transform
	 */
	public ITransformer<MbMessageAssembly, String> getTransformer() {
		return new TransformFailureResponse();
	}

	
	/* (non-Javadoc)
	 * @see com.anz.common.compute.impl.CommonJavaCompute#prepareForTransformation(com.anz.common.compute.ComputeInfo, com.ibm.broker.plugin.MbMessageAssembly, com.ibm.broker.plugin.MbMessageAssembly)
	 */
	@Override
	public void prepareForTransformation(ComputeInfo metadata,
		MbMessageAssembly inAssembly, MbMessageAssembly outAssembly) throws Exception {
		super.prepareForTransformation(metadata, inAssembly, outAssembly);		
		
		String transactionId = ComputeUtils.getTransactionId(outAssembly);
		if(transactionId != null) {
			metadata.setMessageId(transactionId);
		}
		
		metadata.addUserDefinedProperty("IncidentArea", (String) getUserDefinedAttribute("INCIDENT_AREA"));
		
	}

	/* (non-Javadoc)
	 * @see com.anz.common.compute.impl.CommonJavaCompute#executeAfterTransformation(com.anz.common.compute.ComputeInfo, com.ibm.broker.plugin.MbMessageAssembly, com.ibm.broker.plugin.MbMessageAssembly)
	 */
	@Override
	public void executeAfterTransformation(ComputeInfo metadata,
			MbMessageAssembly inAssembly, MbMessageAssembly outAssembly)
			throws Exception {
		// TODO Auto-generated method stub
		super.executeAfterTransformation(metadata, inAssembly, outAssembly);		

		TransformType transformType = getTransformationType();
		
		MessageFlowProxy flow = ComputeUtils.getFlowProxy("TESTNODE_root", "default", "HttpToHttp-app", "Main");
		String fireAndForget = (String) flow.getUserDefinedProperty("FIRE_AND_FORGET");
		
		if("true".equalsIgnoreCase(fireAndForget)) {
			logger.info("Fire and forget pattern");
			if(transformType.equals(TransformType.MQ_MQ) || transformType.equals(TransformType.MQ_HTTP)) {
				logger.info("Setting output node to none");
				metadata.setOutputTarget(OutputTarget.NONE);
			} else {
				MbElement replyStatusCode = ComputeUtils.setHttpReplyStatus(outAssembly, "200"); 
				logger.info("Setting http reply status code: {} ", replyStatusCode);
				transformSuccessMessageForFireAndForget(outAssembly);
			}
		}
		if(transformType.equals(TransformType.MQ_MQ) || transformType.equals(TransformType.MQ_HTTP)) {
			MbElement root = outAssembly.getMessage().getRootElement();
			MbElement replyToQ = root.getFirstElementByPath("/MQMD/ReplyToQ");
			if(replyToQ != null) 
				replyToQ.setValue(replyToQ.getValueAsString().trim());
			MbElement replyToQMgr = root.getFirstElementByPath("/MQMD/ReplyToQMgr");
			if(replyToQMgr != null) 
				replyToQMgr.setValue(replyToQMgr.getValueAsString().trim());
			if(replyToQ != null && replyToQ.getValueAsString() != null && !(replyToQ.getValueAsString()).isEmpty()) {
				logger.info("Setting output node to default. replyToQ: {} replyToQMgr: {}", replyToQ, replyToQMgr);
				metadata.setOutputTarget(OutputTarget.DEFAULT);
				ComputeUtils.setElementInTree(replyToQ.getValueAsString(), outAssembly.getLocalEnvironment(), "Destination","MQ","DestinationData", "queueName" );
				ComputeUtils.setElementInTree(replyToQMgr.getValueAsString(), outAssembly.getLocalEnvironment(), "Destination", "MQ", "DestinationData", "queueManagerName");
			}
		}
	}

	/**
	 * Remove the error message from the body for fire and forget pattern
	 * Override if you want to inject some specific message
	 * @param outAssembly
	 * @throws Exception
	 */
	protected void transformSuccessMessageForFireAndForget(MbMessageAssembly outAssembly) throws Exception {
		
		ComputeUtils.removeMessageBody(outAssembly.getMessage());
		
	}
	
	

}
