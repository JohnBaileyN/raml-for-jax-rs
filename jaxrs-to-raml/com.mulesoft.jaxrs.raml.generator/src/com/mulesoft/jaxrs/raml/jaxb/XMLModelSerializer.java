package com.mulesoft.jaxrs.raml.jaxb;

import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.raml.schema.model.DefaultValueFactory;
import org.raml.schema.model.IMapSchemaProperty;
import org.raml.schema.model.ISchemaProperty;
import org.raml.schema.model.ISchemaType;
import org.raml.schema.model.serializer.ISerializationNode;
import org.raml.schema.model.serializer.StructuredModelSerializer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.mulesoft.jaxrs.raml.annotation.model.StructureType;

public class XMLModelSerializer extends StructuredModelSerializer {

	@Override
	protected ISerializationNode createNode(ISchemaType type, ISchemaProperty prop, ISerializationNode parent) {
		StructureType st = prop!=null?prop.getStructureType():type.getParentStructureType();
		if(st!=StructureType.COMMON && parent==null){
			throw new UnsupportedOperationException("Root structure not supported by the serializer.");
		}
		
		if(st==StructureType.MAP){
			return null;
		}
		String name = getPropertyName(type, prop);
		return new Node(name,parent);
	}

	private static String getPropertyName(ISchemaType type, ISchemaProperty prop) {
		return prop != null ? type.getQualifiedPropertyName(prop) : type.getName();
	}
	
	private class Node implements ISerializationNode {

		public Node(String name, ISerializationNode parent) {
			if (parent != null) {
				this.document = ((Node)parent).document;
			} else {
				try {
					this.document = DocumentBuilderFactory.newInstance()
							.newDocumentBuilder().newDocument();
				} catch (ParserConfigurationException e) {
					e.printStackTrace();
				}
			}
			this.element = this.document.createElement(name);
			if(parent==null){
				this.document.appendChild(this.element);
			}
		}		

		private Node(String name, Document document) {
			super();
			this.document = document;
			this.element = document.createElement(name);
		}
		
		private Document document;

		private Element element;

		@Override
		public void processProperty(ISchemaType type, ISchemaProperty prop, ISerializationNode childNode) {
			
			ISchemaType propType = prop.getType();
			if(prop.isAttribute()){			
				this.element.setAttribute(type.getQualifiedPropertyName(prop), DefaultValueFactory.getDefaultValue(prop).toString());
				return;
			}
			else{				
				if(prop.getStructureType()==StructureType.MAP){
					
					IMapSchemaProperty msp = (IMapSchemaProperty) prop;
					ISchemaType keyType = msp.getKeyType();
					ISchemaType valueType = msp.getValueType();
					
					String name = getPropertyName(type,prop);
					Element mapElement = document.createElement(name);
					if(this.element!=null){
						this.element.appendChild(mapElement);
					}
					else{
						this.document.appendChild(mapElement);
					}
					
					Element entryElement = this.document.createElement("entry");
					mapElement.appendChild(entryElement);
					if(keyType!=null&&keyType.isSimple()){
						Element keyElement = this.document.createElement("key");
						keyElement.setTextContent(DefaultValueFactory.getDefaultValue(keyType).toString());
						entryElement.appendChild(keyElement);
					}
					else{
						Node keyNode = new Node("key", this.document);
						XMLModelSerializer.this.process(keyType, keyNode);
						entryElement.appendChild(keyNode.element);
					}
					if(valueType!=null&&valueType.isSimple()){
						Element valueElement = this.document.createElement("value");
						valueElement.setTextContent(DefaultValueFactory.getDefaultValue(valueType).toString());
						entryElement.appendChild(valueElement);
					}
					else{
						Node valueNode = new Node("value", this.document);
						XMLModelSerializer.this.process(valueType, valueNode);
						entryElement.appendChild(valueNode.element);
					}
				}
				else{
					Element childElement = ((Node)childNode).element;
					if(propType!=null&&propType.isSimple()||prop.isGeneric()){						
						childElement.setTextContent(DefaultValueFactory.getDefaultValue(prop).toString());
					}
					this.element.appendChild(childElement);
					if(prop.getStructureType()==StructureType.COLLECTION){
						this.element.appendChild(childElement.cloneNode(true));
					}
				}
			}		
		}

		@Override
		public String getStringValue() {
			
			try{
				TransformerFactory newInstance = TransformerFactory.newInstance();
				newInstance.setAttribute("indent-number", 4);
				Transformer newTransformer = newInstance.newTransformer();
				
				newTransformer.setOutputProperty(OutputKeys.INDENT,"yes");
				
				StringWriter writer = new StringWriter();
				newTransformer.transform(new DOMSource(this.document),new StreamResult(writer));
				writer.close();
				return writer.toString();
			}catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}

	}


}
