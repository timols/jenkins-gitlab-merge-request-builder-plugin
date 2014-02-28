package org.jenkinsci.plugins.gitlab;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


public class GitlabMergeRequestStatus {
    private HashMap<String, String> mergeRequestStatus;
    private Document document;
    private String jenkinsHome = System.getenv("JENKINS_HOME");
    private String filePath = jenkinsHome + "/_gitlabMergeRequests.xml";

    protected GitlabMergeRequestStatus() {
    	mergeRequestStatus = new HashMap<String, String>();
    }

    public void load() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document document = db.parse(filePath);

            NodeList root = document.getChildNodes();
            NodeList requests = root.item(0).getChildNodes();
            for (int i = 0; i < requests.getLength(); i++) {
            	Node _request = requests.item(i);
            	if (_request.hasChildNodes()) {
	                String _requestId = _request.getNodeName().replace("r", "");
	                String _requestLatestCommit = _request.getTextContent();
	                setLatestCommitOfMergeRequest(_requestId, _requestLatestCommit);
            	}
            }
        } catch (FileNotFoundException e) {
        	System.out.println(e.getMessage());
        } catch (ParserConfigurationException e) {
            System.out.println(e.getMessage());
        } catch (SAXException e) {
            System.out.println(e.getMessage());
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    public String getLatestCommitOfMergeRequest(String _requestId) {
        if (mergeRequestStatus.containsKey(_requestId)) {
            return mergeRequestStatus.get(_requestId);
        }
        return null;
        
    }

    public void setLatestCommitOfMergeRequest(String _requestId,
            String latestCommit) {
        mergeRequestStatus.put(_requestId, latestCommit);
        update();
    }

    private void update() {
        init();
        Element root = this.document.createElement("requests");
        this.document.appendChild(root);
        for (String _requestId: mergeRequestStatus.keySet()) {
            Element request = this.document.createElement("r" + _requestId);
            request.appendChild(this.document.createTextNode(mergeRequestStatus.get(_requestId)));
            root.appendChild(request);
        }

        TransformerFactory tf = TransformerFactory.newInstance();
        try {
            Transformer transformer = tf.newTransformer();
            DOMSource source = new DOMSource(document);
			transformer.setOutputProperty(OutputKeys.ENCODING, "gb2312");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            PrintWriter pw = new PrintWriter(new FileOutputStream(filePath));
            StreamResult result = new StreamResult(pw);
            transformer.transform(source, result);
        } catch (TransformerConfigurationException e) {
            System.out.println(e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
        } catch (TransformerException e) {
            System.out.println(e.getMessage());
        }
    }

    private void init() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory
                .newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            this.document = builder.newDocument();
        } catch (ParserConfigurationException e) {
            System.out.println(e.getMessage());
        }
    }

}
