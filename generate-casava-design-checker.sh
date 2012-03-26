#!/bin/bash

GWT_PATH=~/Téléchargements/gwt-2.4.0
PROJECT_NAME=CasavaDesignValidator

BASEDIR=`dirname $0`
if [ ! -d $BASEDIR/target ]; then
  mkdir $BASEDIR/target
fi
cd $BASEDIR/target

EOULSAN_DIR=..
EOULSAN_PACKAGE=fr.ens.transcriptome.eoulsan
EOULSAN_PACKAGE_PATH=`echo $EOULSAN_PACKAGE | sed 's/\./\//g'`
PACKAGE=fr.ens.transcriptome.cdv
PACKAGE_PATH=`echo $PACKAGE | sed 's/\./\//g'`


rm -rf $PROJECT_NAME
$GWT_PATH/webAppCreator -out $PROJECT_NAME $PACKAGE.CasavaDesignValidator
rm  $PROJECT_NAME/src/fr/ens/transcriptome/cdv/client/*
rm  $PROJECT_NAME/src/fr/ens/transcriptome/cdv/server/*
rm  $PROJECT_NAME/src/fr/ens/transcriptome/cdv/shared/*


for f in `echo EoulsanException.java`
do
	sed "s/package $EOULSAN_PACKAGE/package $PACKAGE.client/" $EOULSAN_DIR/src/main/java/$EOULSAN_PACKAGE_PATH/$f > $PROJECT_NAME/src/$PACKAGE_PATH/client/$f
done

for f in `echo CasavaDesign.java CasavaDesignUtil.java CasavaSample.java`
do
	sed "s/package $EOULSAN_PACKAGE.illumina/package $PACKAGE.client/" $EOULSAN_DIR/src/main/java/$EOULSAN_PACKAGE_PATH/illumina/$f | sed "s/import $EOULSAN_PACKAGE.illumina.io/import $PACKAGE.client/" | sed "s/import $EOULSAN_PACKAGE.illumina/import $PACKAGE.client/" |  sed "s/import $EOULSAN_PACKAGE/import $PACKAGE.client/"  > $PROJECT_NAME/src/$PACKAGE_PATH/client/$f
done

for f in `echo CasavaDesignReader.java AbstractCasavaDesignTextReader.java`
do
	sed "s/package $EOULSAN_PACKAGE.illumina.io/package $PACKAGE.client/" $EOULSAN_DIR/src/main/java/$EOULSAN_PACKAGE_PATH/illumina/io/$f | sed "s/import $EOULSAN_PACKAGE.illumina.io/import $PACKAGE.client/" | sed "s/import $EOULSAN_PACKAGE.illumina/import $PACKAGE.client/" |  sed "s/import $EOULSAN_PACKAGE/import $PACKAGE.client/" > $PROJECT_NAME/src/$PACKAGE_PATH/client/$f
done

#
# Add main class 
#

cat > $PROJECT_NAME/src/$PACKAGE_PATH/client/$PROJECT_NAME.java << EOF
package $PACKAGE.client;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.RootLayoutPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.TabLayoutPanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class $PROJECT_NAME implements EntryPoint {

  private static final String DEFAULT_INDEXES = "#Index id, Sequence\n"
      + "I1=ATCACG\n" + "I2=CGATGT\n" + "I3=TTAGGC\n" + "I4=TGACCA\n"
      + "I5=ACAGTG\n" + "I6=GCCAAT\n" + "I7=CAGATC\n" + "I8=ACTTGA\n"
      + "I9=GATCAG\n" + "I10=TAGCTT\n" + "I11=GGCTAC\n" + "I12=CTTGTA\n"
      + "I13=AGTCAA\n" + "I14=AGTTCC\n" + "I15=ATGTCA\n" + "I16=CCGTCC\n"
      + "I17=GTAGAG\n" + "I18=GTCCGC\n" + "I19=GTGAAA\n" + "I20=GTGGCC\n"
      + "I21=GTTTCG\n" + "I22=CGTACG\n" + "I23=GAGTGG\n" + "I24=GGTAGC\n"
      + "I25=ACTGAT\n" + "I26=ATGAGC\n" + "I27=ATTCCT\n" + "I28=CAAAAG\n"
      + "I29=CAACTA\n" + "I30=CACCGG\n" + "I31=CACGAT\n" + "I32=CACTCA\n"
      + "I33=CAGGCG\n" + "I34=CATGGC\n" + "I35=CATTTT\n" + "I36=CCAACA\n"
      + "I37=CGGAAT\n" + "I38=CTAGCT\n" + "I39=CTATAC\n" + "I40=CTCAGA\n"
      + "I41=GACGAC\n" + "I42=TAATCG\n" + "I43=TACAGC\n" + "I44=TATAAT\n"
      + "I45=TCATTC\n" + "I46=TCCCGA\n" + "I47=TCGAAG\n" + "I48=TCGGCA\n";

  private static String DEFAULT_RESULT_MSG = "<pre>No valid design entered.</pre>";

  private final TextArea inputTextarea = new TextArea();
  private final TextArea indexesTextarea = new TextArea();
  private final HTML outputHTML = new HTML();
  private final TextBox flowcellTextBox = new TextBox();
  private final Button button = new Button("Check the Casava design");

  private boolean first = true;

  public static final void updateDesignWithIndexes(final CasavaDesign design,
      final String indexes) {

    if (design == null || indexes == null)
      return;

    final Map<String, String> map = new HashMap<String, String>();

    String[] lines = indexes.split("\n");

    for (String line : lines) {

      if (line.trim().startsWith("#"))
        continue;

      String[] fields = line.split("=");
      if (fields.length != 2)
        continue;
      map.put(fields[0].trim(), fields[1].trim());
    }

    for (CasavaSample sample : design) {
      if (map.containsKey(sample.getIndex()))
        sample.setIndex(map.get(sample.getIndex()));
    }

  }

  private String getFlowcellId(final String s) {

    if (s == null || s.trim().length() == 0)
      return null;

    if (s.indexOf('_') == -1)
      return s.trim();

    String[] fields = s.split("_");

    return fields[fields.length - 1].trim();
  }

  public void onModuleLoad() {

    // Set the layouts
    final TabLayoutPanel tp = new TabLayoutPanel(1.5, Unit.EM);
    tp.add(new ScrollPanel(inputTextarea), "[Input Casava design]");
    tp.add(outputHTML, "[CSV Casava design]");
    tp.add(new ScrollPanel(indexesTextarea), "[The indexes]");
    tp.setHeight("100%");
    tp.setWidth("100%");

    //final DockLayoutPanel fp = new DockLayoutPanel(Unit.EM);
    //fp.addWest(new HTML("Flow cell ID: "), 20);
    //fp.addEast(button, 20);
    //fp.add(flowcellTextBox);

    //DockLayoutPanel dlp = new DockLayoutPanel(Unit.EM);
    //dlp.addNorth(new HTML("Casava design checker"), 2);
    //dlp.addSouth(fp, 2);
    //dlp.add(tp);

    //RootLayoutPanel rp = RootLayoutPanel.get();
    //rp.add(dlp);

    RootPanel.get("flowcellidFieldContainer").add(flowcellTextBox);
    RootPanel.get("sendButtonContainer").add(button);
    RootPanel.get("tabsContainer").add(tp);

    // From demo
    // RootPanel.get("nameFieldContainer").add(nameField);
    // RootPanel.get("sendButtonContainer").add(sendButton);
    // RootPanel.get("errorLabelContainer").add(errorLabel);


    // Initialize widget values
    indexesTextarea.setText(DEFAULT_INDEXES);
    indexesTextarea.setVisibleLines(40);
    indexesTextarea.setSize("99%","100%");
    //indexesTextarea.setCharacterWidth(150);
    flowcellTextBox.setText(Window.Location.getParameter("flowcellid"));
    inputTextarea.setText("[Paste here your Casava design]");
    //inputTextarea.setCharacterWidth(150);
    inputTextarea.setVisibleLines(40);
    inputTextarea.setSize("99%","100%");
    outputHTML.setHTML(DEFAULT_RESULT_MSG);

    // Set the action on button click
    this.button.addClickHandler(new ClickHandler() {

      public void onClick(ClickEvent event) {

        // Get input text
        String inputText = inputTextarea.getText();

        // Clear ouput
        outputHTML.setHTML(DEFAULT_RESULT_MSG);

        try {
          CasavaDesign design =
              CasavaDesignUtil.parseTabulatedDesign(inputText);

          updateDesignWithIndexes(design,
              indexesTextarea.getText());

          CasavaDesignUtil.checkCasavaDesign(design,
              getFlowcellId(flowcellTextBox.getText()));

          //boolean b = Window.confirm("Warning message:\ntoto\ntiti");

          //if (b) {
            outputHTML.setHTML("<pre>"
                + CasavaDesignUtil.toCSV(design) + "</pre>");
            tp.selectTab(1);
          //}
        } catch (IOException e) {
          Window.alert(e.getMessage());
        } catch (EoulsanException e) {
          Window.alert(e.getMessage());
        }

      }

    });

    // Clear tip message in input text area
    this.inputTextarea.addClickHandler(new ClickHandler() {

      @Override
      public void onClick(ClickEvent event) {

        if (first) {

          inputTextarea.setText("");
          first = false;
        }
      }
    });

  }
}
EOF

cat > $PROJECT_NAME/war/$PROJECT_NAME.html << EOF
<!doctype html>
<!-- The DOCTYPE declaration above will set the     -->
<!-- browser's rendering engine into                -->
<!-- "Standards Mode". Replacing this declaration   -->
<!-- with a "Quirks Mode" doctype is not supported. -->

<html>
  <head>
    <meta http-equiv="content-type" content="text/html; charset=UTF-8">

    <!--                                                               -->
    <!-- Consider inlining CSS to reduce the number of requested files -->
    <!--                                                               -->
    <link type="text/css" rel="stylesheet" href="CasavaDesignValidator.css">

    <!--                                           -->
    <!-- Any title is fine                         -->
    <!--                                           -->
    <title>Casava design checker</title>
    
    <!--                                           -->
    <!-- This script loads your compiled module.   -->
    <!-- If you add any GWT meta tags, they must   -->
    <!-- be added before this line.                -->
    <!--                                           -->
    <script type="text/javascript" language="javascript" src="casavadesignvalidator/casavadesignvalidator.nocache.js"></script>
  </head>

  <!--                                           -->
  <!-- The body can have arbitrary html, or      -->
  <!-- you can leave the body empty if you want  -->
  <!-- to create a completely dynamic UI.        -->
  <!--                                           -->
  <body>

    <!-- OPTIONAL: include this if you want history support -->
    <iframe src="javascript:''" id="__gwt_historyFrame" tabIndex='-1' style="position:absolute;width:0;height:0;border:0"></iframe>
    
    <!-- RECOMMENDED if your web app will not function without JavaScript enabled -->
    <noscript>
      <div style="width: 22em; position: absolute; left: 50%; margin-left: -11em; color: red; background-color: white; border: 1px solid red; padding: 4px; font-family: sans-serif">
        Your web browser must have JavaScript enabled
        in order for this application to display correctly.
      </div>
    </noscript>

<h1>Casava design checker</h1>

    <table align="center">
      <!--tr>
        <td colspan="2" style="font-weight:bold;">Please enter your name:</td>        
      </tr-->
      <tr>
        <td>Flow cell id or run id (optional):</td>
        <td id="flowcellidFieldContainer"></td>
        <td id="nameFieldContainer"></td>
        <td id="sendButtonContainer"></td>
      </tr>
      <tr>
        <!td colspan="2" style="color:red;" id="errorLabelContainer"></td-->
      </tr>
    </table>
   
    <!--p/--> 
  
    <table align="center" width="90%" >
      <tr><td id="tabsContainer" height="700px"/></tr>
    </table>

    <!--p/--> 
        
    <!--table align="center" >
      <tr><td id="sendButtonContainer/></tr>
    </table-->


 
  </body>
</html>
EOF

# Compile

cd $PROJECT_NAME
ant build

mv war ../$PROJECT_NAME-tmp
rm -rf ../$PROJECT_NAME-tmp/WEB-INF
cd ..
rm -rf $PROJECT_NAME
mv $PROJECT_NAME-tmp $PROJECT_NAME
