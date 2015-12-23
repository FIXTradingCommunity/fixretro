//
// FixRetro.java - Retrofit FIX 4.4 XML info to other non-XML versions
//
// AK, 11 May 2011, initial version
//
// Foreach FIX version with fixml="0"
// try to augment from FIX.4.4 and set fixml="1".
//

package org.fixprotocol.contrib.retro;

//...simports:0:
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.StringReader;
import java.io.BufferedReader;
import java.io.CharArrayWriter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.stream.StreamResult;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ErrorHandler;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.w3c.dom.Comment;
//...e

public class FixRetro
  {
//...sfirstElement:2:
protected static Element firstElement(Node n)
  {
  while ( n != null && n.getNodeType() != Node.ELEMENT_NODE )
    n = n.getNextSibling();
  return (Element) n;
  }
//...e
//...snextElement:2:
protected static Element nextElement(Node n)
  {
  do
    n = n.getNextSibling();
  while ( n != null && n.getNodeType() != Node.ELEMENT_NODE );
  return (Element) n;
  }
//...e

  protected int compIdNext = 1000000; // for any new components we generate

//...srefRepeatingGroup:2:
// Look for repeatingGroups directly within element
// Element is either the message, or a component (when invoked recursively)
// Replace each with componentRefs to a new component containing the repeatingGroup

protected void refRepeatingGroup(
  String context,
  Element e, // message or component
  Element ecomponents,
  Map<String,Element> e44components,
  String namePrefix,
  String category
  )
  throws FixRetroException
  {
  NodeList nl = e.getChildNodes();
  for ( int i = 0; i < nl.getLength(); i++ )
    {
    Node n = nl.item(i);
    if ( n.getNodeType() == Node.ELEMENT_NODE )
      {
      Element e2 = (Element) n;
      String e2name = e2.getNodeName();
      if ( e2name.equals("fieldRef") )
        ; // we're not interested in these
      else if ( e2name.equals("componentRef") )
        ; // we're not interested in these
      else if ( e2name.equals("repeatingGroup") )
        {
/*
        if ( category.equals("Session") )
          ; // we know that the Login message is a problem
            // but it doesn't matter, so leave it alone
        else
*/
          {
          String rgId = e2.getAttribute("id");
          String context2 = context+"/repeatingGroup[@id='"+rgId+"']";
          String compId = Integer.toString(compIdNext++);

          // Dodgy heuristic to try to find a name
          String compName     = null;
          String compAbbrName = null;
          for ( String name : e44components.keySet() )
            {
            Element e44c = e44components.get(name);
            Element e44rg = firstElement( e44c.getFirstChild() );
            if ( e44rg != null && e44rg.getNodeName().equals("repeatingGroup") )
              if ( e2.getAttribute("id").equals(e44rg.getAttribute("id")) )
                {
                compName     = name;
                compAbbrName = e44c.getAttribute("abbrName");
                break;
                }
            }
          if ( compName == null )
            // We've seen this happen for Logon messages
            {
            compName     = "RefMsg";
            compAbbrName = "RefMsg";
            parseWarning(context2, "don't know how to name group, probably a Logon message");
            }

          compName = namePrefix+"_"+compName;
          Document d = e.getOwnerDocument();
          Element ec = d.createElement("component");
          ec.setAttribute("id", compId);
          ec.setAttribute("name", compName);
          ec.setAttribute("abbrName", compAbbrName);
          ec.setAttribute("category", category);
          ec.setAttribute("notReqXML", "0");
          ec.setAttribute("repeating", "1");
          ec.setAttribute("type", "ImplicitBlockRepeating");
          ec.setAttribute("added", "FIX.4.4"); // well, sort of

          String compNameRef;
          String compIdRef;
          if ( compName.equals("StandardHeader_Hop") )
            {
            compNameRef = "Hop";
            compIdRef   = e44components.get(compNameRef).getAttribute("id");
            parseWarning(context2, "reference to StandardHeader_Hop will be replaced with reference to Hop");
            }
          else
            {
            compNameRef = compName;
            compIdRef   = compId;
            }

          Element ecr = d.createElement("componentRef");
          ecr.setAttribute("id", compIdRef);
          ecr.setAttribute("name", compNameRef);
          ecr.setAttribute("added", "FIX.4.4"); // well, sort of
          ecr.setAttribute("required", e2.getAttribute("required"));
          ecr.setAttribute("legacyPosition", e2.getAttribute("legacyPosition"));
            // This will leave gaps, as the group probably has a number of positions
          ecr.setAttribute("legacyIndent", e2.getAttribute("legacyIndent"));

          e.replaceChild(ecr, e2); // replace repeatingGroup with componentRef
          ec.appendChild(e2); // put repeatingGroup into component
          if ( ! compNameRef.equals("Hop") )
            ecomponents.appendChild(ec); // append component to components
          parseWarning(context2, "replaced repeatingGroup with componentRef "+compNameRef);

          // The repeatingGroup we just put inside the new component
          // could itself contain repeatingGroups, so process these too.
          refRepeatingGroup(context2, e2, ecomponents, e44components, compName, category);

          // The repeatingGroup could contain fields which belong in a component
          refNestedComponents(context2, e2, null, ecomponents, e44components, compName, category);
          }
        }
      else
        parseWarning(context, "skipping element "+e2name);
      }
    }
  }
//...e

//...sparseError:2:
protected void parseError(String context, String error)
  throws FixRetroException
  {
  throw new FixRetroException(context+": "+error);
  }
//...e
//...sparseWarning:2:
protected void parseWarning(String context, String warning)
  {
  System.out.println(context+": "+warning);
  }
//...e
//...sparseAttribute:2:
protected String parseAttribute(String context, Element e, String attr)
  throws FixRetroException
  {
  if ( ! e.hasAttribute(attr) )
    parseError(context, "@"+attr+" missing");
  return e.getAttribute(attr);
  }

protected String parseAttribute(String context, Element e, String attr, String def)
  {
  return e.hasAttribute(attr) ? e.getAttribute(attr) : def;
  }
//...e
//...sparseField:2:
protected void parseField(
  String context,
  Element e,
  Map<String,Element> e44fields,
  String version
  )
  throws FixRetroException
  {
  String name = e.getAttribute("name");
  String idStr = parseAttribute(context, e, "id");
  context += "[@id='"+idStr+"']";
  Element e44field = e44fields.get(idStr);
  if ( e44field != null )
    {
    String name44 = e44field.getAttribute("name");
    if ( ! name.equals(name44) )
      parseWarning(context, name+" is "+name44+" in FIX.4.4");
    String notReqXML = e44field.getAttribute("notReqXML");
    if ( notReqXML.equals("0") )
      e.setAttribute("notReqXML", "0");
    String abbrName = e44field.getAttribute("abbrName");
    e.setAttribute("abbrName", abbrName);
    String baseCategory = e44field.getAttribute("baseCategory");
    if ( ! baseCategory.equals("") )
      {
      e.setAttribute("baseCategory", baseCategory);
      String baseCategoryAbbrName = e44field.getAttribute("baseCategoryAbbrName");
      e.setAttribute("baseCategoryAbbrName", baseCategoryAbbrName);
      }
    }
  else
    {
    parseWarning(context, name+" not in FIX.4.4");
    e.setAttribute("notReqXML", "0");
    e.setAttribute("abbrName", name);
    }
  if ( ( name.equals("PartySubID") || name.equals("NestedPartySubID") ) && version.equals("FIX.4.3") )
    // Unfortunately we changed from a single SubID in FIX.4.3 to a group in FIX.4.4
    {
    e.setAttribute("abbrName", "SubID");
    parseWarning(context, name+" abbrName changed from ID to SubID to avoid attribute clash");
    }
  if ( name.startsWith("No") && e.getAttribute("type").equals("int") )
    {
    e.setAttribute("type", "NumInGroup");
    parseWarning(context, name+" type corrected to NumInGroup");
    }
  }
//...e
//...sparseFields:2:
protected void parseFields(
  String context,
  Element e,
  Map<String,Element> e44fields,
  String version
  )
  throws FixRetroException
  {
  NodeList nl = e.getChildNodes();
  for ( int i = 0; i < nl.getLength(); i++ )
    {
    Node n = nl.item(i);
    if ( n.getNodeType() == Node.ELEMENT_NODE )
      {
      Element e2 = (Element) n;
      String e2name = e2.getNodeName();
      if ( e2name.equals("field") )
        parseField(context+"/field", e2, e44fields, version);
      else
        parseWarning(context, "skipping "+e2name);
      }
    }
  }
//...e
//...sparseComponent:2:
protected void parseComponent(
  String context,
  Element e,
  Element ecomponents,
  Map<String,Element> e44components
  )
  throws FixRetroException
  {
  String name = e.getAttribute("name");
  String idStr = parseAttribute(context, e, "id");
  int id = Integer.parseInt(idStr);
  context += "[@id='"+idStr+"']";
  Element e44component = e44components.get(name);
  if ( e44component != null )
    {
    String notReqXML = e44component.getAttribute("notReqXML");
    if ( notReqXML.equals("0") )
      e.setAttribute("notReqXML", "0");
    String category = e44component.getAttribute("category");
    e.setAttribute("category", category);
    String abbrName = e44component.getAttribute("abbrName");
    e.setAttribute("abbrName", abbrName);
    }
  else
    {
    int i = name.lastIndexOf("_");
    String abbrName = ( i != -1 ) ? name.substring(i+1) : name;
    parseWarning(context, name+" not in FIX.4.4, using "+abbrName);
    e.setAttribute("notReqXML", "0");
    e.setAttribute("abbrName", abbrName);
    }

  Element erg = firstElement(e.getFirstChild());
  if ( erg != null && erg.getNodeName().equals("repeatingGroup") && nextElement(erg) == null )
    ; // component contains one repeatingGroup only
  else
    // Fix up any repeatingGroups within the component
    refRepeatingGroup(context, e, ecomponents, e44components, name, e.getAttribute("category"));
  }
//...e
//...sparseComponents:2:
protected void parseComponents(
  String context,
  Element e,
  Map<String,Element> e44components
  )
  throws FixRetroException
  {
  NodeList nl = e.getChildNodes();
  for ( int i = 0; i < nl.getLength(); i++ )
    {
    Node n = nl.item(i);
    if ( n.getNodeType() == Node.ELEMENT_NODE )
      {
      Element e2 = (Element) n;
      String e2name = e2.getNodeName();
      if ( e2name.equals("component") )
        parseComponent(context+"/component", e2, e, e44components);
      else
        parseWarning(context, "skipping "+e2name);
      }
    }
  }
//...e
//...sparseMessage:2:
//...sfieldRefInComponent:2:
// Does the element contain a fieldRef to a given named field?

protected static boolean fieldRefInComponent(Element e, String name)
  {
  NodeList nl = e.getChildNodes();
  for ( int i = 0; i < nl.getLength(); i++ )
    {
    Node n = nl.item(i);
    if ( n.getNodeType() == Node.ELEMENT_NODE )
      {
      Element e2 = (Element) n;
      String e2name = e2.getNodeName();
      if ( e2name.equals("fieldRef") && e2.getAttribute("name").equals(name) )
        return true;
      }
    }
  return false;
  }
//...e
//...sbelongsInComponent:2:
// Does a given fieldRef belong in a referenced component.
// If so, returns the referenced component.

protected static Element belongsInComponent(
  Element e, // or null
  String name,
  Map<String,Element> e44components
  )
  {
  if ( name.equals("LegCurrency") )
    // LegCurrency is always alongside an InstrumentLeg in FIX.4.3,
    // but in FIX.4.4, it is actally a part of the InstrumentLeg.
    // Avoid trying to move in into InstrumentLeg, as this
    // causes clashing XML attribute names in the generated FIXML schema.
    return null;
  if ( e != null )
    {
    NodeList nl = e.getChildNodes();
    // First, if the fieldRef is in the FIX.4.4 message,
    // then it doesn't belong in a referenced component
    for ( int i = 0; i < nl.getLength(); i++ )
      {
      Node n = nl.item(i);
      if ( n.getNodeType() == Node.ELEMENT_NODE )
        {
        Element e2 = (Element) n;
        String e2name = e2.getNodeName();
        if ( e2name.equals("fieldRef") && e2.getAttribute("name").equals(name) )
          return null;
        }
      }
    // Second, look to see if its in a directly
    // referenced component.
    for ( int i = 0; i < nl.getLength(); i++ )
      {
      Node n = nl.item(i);
      if ( n.getNodeType() == Node.ELEMENT_NODE )
        {
        Element e2 = (Element) n;
        String e2name = e2.getNodeName();
        if ( e2name.equals("componentRef") )
          {
          Element ec = e44components.get(e2.getAttribute("name"));
          if ( fieldRefInComponent(ec, name) )
            return ec;
          }
        }
      }
    }
  else
    // We don't have a FIX.4.4 message to compare against
    // so look through all components in FIX.4.4
    for ( String name2 : e44components.keySet() )
      {
      Element ec = e44components.get(name2);
      if ( fieldRefInComponent(ec, name) )
        return ec;
      }
  return null;
  }
//...e
//...sbelongsInComponent2:2:
protected static Element belongsInComponent2(
  String msgName,
  Element e, // or null
  String name,
  Map<String,Element> e44components
  )
  {
  Element v = belongsInComponent(e, name, e44components);
  if ( v == null && msgName.equals("OrderList") )
    // Second chance, look all all components
    v = belongsInComponent(null, name, e44components);
  return v;
  }
//...e
//...srefNestedComponents:2:
protected void refNestedComponents(
  String context,
  Element e, // message or component
  Element e44, // message or component
  Element ecomponents,
  Map<String,Element> e44components,
  String namePrefix,
  String category
  )
  throws FixRetroException
  {
  String msgName = e.getAttribute("name");
  NodeList nl = e.getChildNodes();
  for ( int i = 0; i < nl.getLength(); i++ )
    {
    Node n = nl.item(i);
    if ( n.getNodeType() == Node.ELEMENT_NODE )
      {
      Element e2 = (Element) n;
      String e2name = e2.getNodeName();
      if ( e2name.equals("fieldRef") )
        {
        Element ec44 = belongsInComponent2(msgName, e44, e2.getAttribute("name"), e44components);
        if ( ec44 != null )
          // We found one fieldRef that should be in a referenced component
          // So create the component, reference it, and put fieldRef in it
          {
          String compName = namePrefix+"_"+ec44.getAttribute("name");

          String compId = Integer.toString(compIdNext++);
          Document d = e.getOwnerDocument();
          Element ec = d.createElement("component");
          ec.setAttribute("id", compId);
          ec.setAttribute("name", compName);
          ec.setAttribute("abbrName", ec44.getAttribute("abbrName"));
          ec.setAttribute("category", category);
          ec.setAttribute("notReqXML", "0");
          ec.setAttribute("repeating", "0");
          ec.setAttribute("type", "ImplicitBlock");
          ec.setAttribute("added", "FIX.4.4"); // well, sort of

          Element ecr = d.createElement("componentRef");
          ecr.setAttribute("id", compId);
          ecr.setAttribute("name", compName);
          ecr.setAttribute("added", "FIX.4.4"); // well, sort of
          ecr.setAttribute("required", "1");
          ecr.setAttribute("legacyPosition", e2.getAttribute("legacyPosition"));
          ecr.setAttribute("legacyIndent", e2.getAttribute("legacyIndent"));

          e.replaceChild(ecr, e2); // replace fieldRef with componentRef
          ec.appendChild(e2); // put fieldRef into component
          ecomponents.appendChild(ec); // append component to components
          parseWarning(context, "replaced fieldRef(s) with componentRef "+compName);

          // Look for other following fieldRefs that belong in the
          // same referenced component, and put them in it too
          int iLast = i;
          for ( int j = i+1; j < nl.getLength(); j++ )
            {
            n = nl.item(j);
            if ( n.getNodeType() == Node.ELEMENT_NODE )
              {
              e2 = (Element) n;
              e2name = e2.getNodeName();
              if ( e2name != null && e2name.equals("fieldRef") &&
                ec44 == belongsInComponent2(msgName, e44, e2.getAttribute("name"), e44components) )
                iLast = j;
              }
            }
          for ( ; i+1 <= iLast; i++ )
            {
            n = nl.item(i+1);
            Comment cmt = d.createComment("moved");
            e.replaceChild(cmt, n); // replace fieldRef with comment
            ec.appendChild(n); // put fieldRef into component
            }
          }
        }
      }
    }
  }
//...e

protected void parseMessage(
  String context,
  Element e,
  Element ecomponents,
  Map<String,Element> e44components,
  Map<String,Element> e44messages
  )
  throws FixRetroException
  {
  String idStr = parseAttribute(context, e, "id");
  int id = Integer.parseInt(idStr);
  context += "[@id='"+idStr+"']";
  String msgType = parseAttribute(context, e, "msgType");
  Element e44message = e44messages.get(msgType);
  if ( e44message != null )
    {
    String notReqXML = e44message.getAttribute("notReqXML");
    if ( notReqXML.equals("0") )
      e.setAttribute("notReqXML", "0");
    String category = e44message.getAttribute("category");
    e.setAttribute("category", category);
    String abbrName = e44message.getAttribute("abbrName");
    e.setAttribute("abbrName", abbrName);
    }
  else
    parseWarning(context, msgType+" not in FIX.4.4");

  // Look for repeatingGroups directly within message
  // This happens in older FIX, but is no good for FIXML 4.4 onwards
  refRepeatingGroup(context, e, ecomponents, e44components, e.getAttribute("name"), e.getAttribute("category"));

  // Try to create some order out of chaos
  // Or put another way, try to establish some of the component hierarchy
  // Look through message for any fields that are not in the message in FIX.4.4
  // If we find any, see if they're in a subcomponent of the message
  if ( e44message != null )
    refNestedComponents(context, e, e44message, ecomponents, e44components, e.getAttribute("name"), e.getAttribute("category"));
  }
//...e
//...sparseMessages:2:
protected void parseMessages(
  String context,
  Element e,
  Element ecomponents,
  Map<String,Element> e44components,
  Map<String,Element> e44messages
  )
  throws FixRetroException
  {
  NodeList nl = e.getChildNodes();
  for ( int i = 0; i < nl.getLength(); i++ )
    {
    Node n = nl.item(i);
    if ( n.getNodeType() == Node.ELEMENT_NODE )
      {
      Element e2 = (Element) n;
      String e2name = e2.getNodeName();
      if ( e2name.equals("message") )
        parseMessage(context+"/message", e2, ecomponents, e44components, e44messages);
      else
        parseWarning(context, "skipping "+e2name);
      }
    }
  }
//...e
//...sparseVersion:2:
//...saddOrReplaceField:2:
protected void addOrReplaceField(String context, Element eParent, Element eChild, String name)
  {
  String id = eChild.getAttribute("id");
  for ( Element e = firstElement(eParent.getFirstChild()); e != null; e = nextElement(e) )
    if ( e.getAttribute("id").equals(id) )
      {
      parseWarning(context, "replacing "+name);
      eParent.replaceChild(eChild.cloneNode(true), e);
      return;
      }
  parseWarning(context, "adding "+name);
  eParent.appendChild(eChild.cloneNode(true)); 
  }
//...e

protected void parseVersion(
  String context,
  Element e,
  Element e44abbreviations,
  Element e44categories,
  Element e44sections,
  Map<String,Element> e44datatypes,
  Map<String,Element> e44fields,
  Map<String,Element> e44components,
  Map<String,Element> e44messages
  )
  throws FixRetroException
  {
  String version = e.getAttribute("version");
  e.setAttribute("components", "1");
  e.setAttribute("specUrl", e.getAttribute("specUrl")+"#RETRO");

  // The version won't have these 3 things, so copy them in
  parseWarning(context, "adding abbreviations, categories and sections");
  Node nFirst = firstElement(e.getFirstChild());
  Node nSecond = nextElement(nFirst);
  e.insertBefore(e44abbreviations.cloneNode(true), nFirst);
  e.insertBefore(e44categories   .cloneNode(true), nSecond);
  e.insertBefore(e44sections     .cloneNode(true), nSecond);

  NodeList nl = e.getChildNodes();
  Element e2components = null;
  for ( int i = 0; i < nl.getLength(); i++ )
    {
    Node n = nl.item(i);
    if ( n.getNodeType() == Node.ELEMENT_NODE )
      {
      Element e2 = (Element) n;
      String e2name = e2.getNodeName();
      if ( e2name.equals("abbreviations") )
        ; // we're not interested in these
      else if ( e2name.equals("datatypes") )
        {
        Set<String> d = new HashSet<String>();
        d.addAll(e44datatypes.keySet());
        for ( Element e3 = firstElement(e2.getFirstChild()); e3 != null; e3 = nextElement(e3) )
          d.remove(e3.getAttribute("name"));
        for ( String s : d )
          {
          e2.appendChild(e44datatypes.get(s).cloneNode(true));
          parseWarning(context+"/datatypes", "adding "+s);
          }
        }
      else if ( e2name.equals("categories") )
        ; // we're not interested in these
      else if ( e2name.equals("sections") )
        ; // we're not interested in these
      else if ( e2name.equals("fields") )
        {
        String context2 = context+"/fields";
        parseFields(context2, e2, e44fields, version);
        Element eHop;
        if ( (eHop = e44fields.get("627")) != null )
          addOrReplaceField(context+"/fields", e2, eHop, "NoHops");
        if ( (eHop = e44fields.get("628")) != null )
          addOrReplaceField(context+"/fields", e2, eHop, "HopCompID");
        if ( (eHop = e44fields.get("629")) != null )
          addOrReplaceField(context+"/fields", e2, eHop, "HopSendingTime");
        if ( (eHop = e44fields.get("630")) != null )
          addOrReplaceField(context+"/fields", e2, eHop, "HopRefID");
        }
      else if ( e2name.equals("components") )
        {
        parseComponents(context+"/components", e2, e44components);
        e2components = e2;
        Element eHop;
        if ( (eHop = e44components.get("Hop")) != null )
          {
          parseWarning(context+"/components", "adding Hop");
          e2components.appendChild(eHop.cloneNode(true));
          }
        }
      else if ( e2name.equals("messages") )
        {
        if ( e2components == null )
          parseError(context, "components must preceed messages");
        parseMessages(context+"/messages", e2, e2components, e44components, e44messages);
        }
      else
        parseWarning(context, "skipping element "+e2name);
      }
    }
  }
//...e
//...sparseRepo:2:
protected void parseRepo(String context, Element e)
  throws FixRetroException
  {
  NodeList nl = e.getChildNodes();

  // Find FIX.4.4
  Element e44 = null;
  for ( int i = 0; i < nl.getLength(); i++ )
    {
    Node n = nl.item(i);
    if ( n.getNodeType() == Node.ELEMENT_NODE )
      {
      Element e2 = (Element) n;
      String e2name = e2.getNodeName();
      if ( e2name.equals("fix") )
        {
        String context2 = context+"/fix";
        if ( parseAttribute(context2, e2, "version").equals("FIX.4.4") &&
             parseAttribute(context2, e2, "customVersion", null) == null &&
             parseAttribute(context2, e2, "fixml").equals("1") )
          e44 = e2;
        }
      }
    }
  if ( e44 == null )
    parseError(context, "no (non-custom version of) FIX.4.4 to steal from");

  // Work out where stuff is in FIX.4.4
  Element e44abbreviations = null;
  Element e44categories    = null;
  Element e44sections      = null;
  Map<String,Element> e44datatypes  = new HashMap<String,Element>();
  Map<String,Element> e44fields     = new HashMap<String,Element>();
  Map<String,Element> e44components = new HashMap<String,Element>();
  Map<String,Element> e44messages   = new HashMap<String,Element>();
  NodeList nl44 = e44.getChildNodes();
  for ( int i = 0; i < nl44.getLength(); i++ )
    {
    Node n = nl44.item(i);
    if ( n.getNodeType() == Node.ELEMENT_NODE )
      {
      Element e2 = (Element) n;
      String e2name = e2.getNodeName();
      if ( e2name.equals("abbreviations") )
        e44abbreviations = e2;
      else if ( e2name.equals("categories") )
        e44categories = e2;
      else if ( e2name.equals("sections") )
        e44sections = e2;
      else if ( e2name.equals("datatypes") )
        {
        NodeList nl44datatypes = e2.getChildNodes();
        for ( int j = 0; j < nl44datatypes.getLength(); j++ )
          {
          Node n2 = nl44datatypes.item(j);
          if ( n2.getNodeType() == Node.ELEMENT_NODE )
            {
            Element e3 = (Element) n2;
            e44datatypes.put(e3.getAttribute("name"), e3);
            }
          }
        }
      else if ( e2name.equals("fields") )
        {
        NodeList nl44fields = e2.getChildNodes();
        for ( int j = 0; j < nl44fields.getLength(); j++ )
          {
          Node n2 = nl44fields.item(j);
          if ( n2.getNodeType() == Node.ELEMENT_NODE )
            {
            Element e3 = (Element) n2;
            e44fields.put(e3.getAttribute("id"), e3);
            }
          }
        }
      else if ( e2name.equals("components") )
        {
        NodeList nl44components = e2.getChildNodes();
        for ( int j = 0; j < nl44components.getLength(); j++ )
          {
          Node n2 = nl44components.item(j);
          if ( n2.getNodeType() == Node.ELEMENT_NODE )
            {
            Element e3 = (Element) n2;
            e44components.put(e3.getAttribute("name"), e3);
            }
          }
        }
      else if ( e2name.equals("messages") )
        {
        NodeList nl44messages = e2.getChildNodes();
        for ( int j = 0; j < nl44messages.getLength(); j++ )
          {
          Node n2 = nl44messages.item(j);
          if ( n2.getNodeType() == Node.ELEMENT_NODE )
            {
            Element e3 = (Element) n2;
            e44messages.put(e3.getAttribute("msgType"), e3);
            }
          }
        }
      }
    }

  for ( int i = 0; i < nl.getLength(); i++ )
    {
    Node n = nl.item(i);
    if ( n.getNodeType() == Node.ELEMENT_NODE )
      {
      Element e2 = (Element) n;
      String e2name = e2.getNodeName();
      if ( e2name.equals("fix") )
        {
        String context2 = context+"/fix";
        String version = parseAttribute(context2, e2, "version"); // eg: "FIX.4.4", "FIX.5.0SP1"
        String customVersion = parseAttribute(context2, e2, "customVersion", "");
        context2 += "[@version='"+version+"' and @customVersion='"+customVersion+"']";
        String fixmlStr = parseAttribute(context2, e2, "fixml");
        if ( fixmlStr.equals("0") )
          {
          parseVersion(context2, e2, e44abbreviations, e44categories, e44sections, e44datatypes, e44fields, e44components, e44messages);
          e2.setAttribute("fixml", "1");
          }
        }
      else
        parseWarning(context, "skipping element "+e2name);
      }
    }
  }
//...e

//...sretrofit:2:
public void retrofit(Document d)
  throws FixRetroException
  {
  Element e = d.getDocumentElement();
  if ( ! e.getNodeName().equals("fixRepository") )
    parseError("/", "root element must be fixRepository");
  String editionStr = parseAttribute("/fixRepository", e, "edition");
  int edition = Integer.parseInt(editionStr);
  if ( edition < 2010 )
    parseError("/fixRepository", "@edition must be at least 2010");
  parseRepo("/fixRepository", e);
  }
//...e

//...sreadTextFileUTF8:2:
public static String readTextFileUTF8(String fn)
  throws IOException
  {
  StringBuffer sb = new StringBuffer();
  InputStream is = new FileInputStream(fn);
  InputStreamReader isr = new InputStreamReader(is, "UTF-8");
  BufferedReader br = new BufferedReader(isr);
  String line;
  while ( (line = br.readLine()) != null )
    {
    sb.append(line);
    sb.append("\n");
    }
  br.close();
  return sb.toString();
  }
//...e
//...swriteTextFileUTF8:2:
public static void writeTextFileUTF8(String fn, String text)
  throws IOException
  {
  OutputStream os = new FileOutputStream(fn);
  OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
  osw.write(text);
  osw.close();
  }
//...e
//...sstrToDoc:2:
protected static Document strToDoc(String s)
  throws Exception
  {
  DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
  // not validating
  // Note: we don't support validation against DTDs during parsing
  // and so we can't support ignoringelementcontentwhitespace either.
  dbf.setNamespaceAware(true); // must do this, else can't validate ok
  dbf.setCoalescing(true);
  dbf.setExpandEntityReferences(true);
  dbf.setIgnoringComments(true);
  DocumentBuilder db = dbf.newDocumentBuilder();
  db.setErrorHandler(
    new ErrorHandler()
      {
      public void warning(SAXParseException e)
        throws SAXException
        {
        }
      public void error(SAXParseException e)
        throws SAXException
        {
        }
      public void fatalError(SAXParseException e)
        throws SAXException
        {
        }
      }
    );
  Document d = db.parse(new InputSource(new StringReader(s)));
    // Note: we avoid using new InputStream(new StringInputStream()),
    // as we want to avoid working at the byte level,
    // after all, we already have a String, which is a sequence of chars.
    // Note that StringInputStream is deprecated for this reason.
  return d;
  }
//...e
//...sdocToStr:2:
protected static String docToStr(Document d)
  throws Exception
  {
  TransformerFactory tf = TransformerFactory.newInstance();
  Transformer t = tf.newTransformer(); // the "identity" transformation
  t.setOutputProperty("indent", "yes");
  t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
  CharArrayWriter caw = new CharArrayWriter();
  t.transform(
    new DOMSource((Node) d),
    new StreamResult(caw)
    );
  caw.flush();
  return caw.toString();
  }
//...e
//...smain:2:
public static void main(String[] args)
  {
  try
    {
    if ( args.length == 0 )
      {
      System.out.println("usage: FixRetro FixRepository.xml [UpdatedFixRepository.xml]");
      System.exit(1);
      }
    String s = readTextFileUTF8(args[0]);
    Document d = strToDoc(s);
    FixRetro r = new FixRetro();
    r.retrofit(d);
    s = docToStr(d);
    if ( args.length >= 2 )
      writeTextFileUTF8(args[1], s);
    else
      System.out.println(s);
    }
  catch ( Exception e )
    {
    e.printStackTrace();
    System.exit(1);
    }
  }
//...e
  }
