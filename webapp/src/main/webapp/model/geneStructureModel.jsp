
<!-- geneStructureModel.jsp -->

<div class="collection-of-collections" id="gene-structure-model" style="height:80px">

    <link rel="stylesheet" type="text/css" href="jbrowse_renderer/genome.css">

<style>
/********************************************************
*   invisible containers, 
*   for features that also have a renderClass that gets centered, 
*      and for subfeatures that have rendered children 
*      (currently only subfeatures like this are exons, which have CDS and UTR child divs)
********************************************************/
.feature-name {
    z-index:0;
}
.RefSeq,
.plus-RefSeq,
.minus-RefSeq  {
    background-color: lightgray;
    height: 7px;
    z-index:0;
}
.RefSeq-CDS,
.plus-RefSeq-CDS,
.minus-RefSeq-CDS  {
    background-color: rgb(79,48,255);
    height: 11px;
    border: 1px solid gray;
    z-index:0;
}

.RefSeq-UTR,
.plus-RefSeq-UTR,
.minus-RefSeq-UTR {
    background-color: rgb(79,48,255);
    height: 9px;
    z-index:0;
}
</style>

<script>
var pid = '<c:out value="${gene.primaryIdentifier}"/>';

// only needed if multiple assemblies
//var assemblyMap = {
//    "Zm-B73-REFERENCE-NAM-5.0" : "862",
//    "B73_RefGen_v3" : "20",
//    "B73_RefGen_v4" : "545"
//};

var organismMap={
    "A. cephalotes": "11040",
    "A. cerana": "4565",
    "A. colombica": "546953",
    "A. dorsata": "6998",
    "A. echinatior": "224",
    "A. florea": "632646",
    "A. mellifera": "22",
    "A. rosae": "791886",
    "B. bifarius": "646318",
    "B. impatiens": "13877",
    "B. terrestris": "19339",
    "B. treatae": "640796",
    "B. vancouverensis nearcticus": "639632",
    "B. vosnesenskii": "647569",
    "C. calcarata": "27465",
    "C. cinctus": "793644",
    "C. costatus": "548505",
    "C. floridanum": "795622",
    "C. floridanus": "24950",
    "C. insularis": "649000",
    "C. obscurior": "25609",
    "D. alloeum": "649457",
    "D. novaeangliae": "94619",
    "D. quadriceps": "80494",
    "E. mexicana": "98799",
    "F. arisanus": "792600",
    "F. exsecta": "652772",
    "H. laboriosa": "286412",
    "H. saltator": "313980",
    "L. albipes": "314839",
    "L. humile": "356274",
    "M. demolitor": "800464",
    "M. genalis": "667295",
    "M. pharaonis": "368463",
    "M. quadrifasciata": "365574",
    "M. rotundata": "359306",
    "N. fulva": "777679",
    "N. lecontei": "802260",
    "N. melanderi": "681794",
    "N. vitripennis": "681356",
    "O. abietinus": "806785",
    "O. bicornis bicornis": "781512",
    "O. biroi": "384187",
    "O. brunneus": "780550",
    "O. lignaria": "791736",
    "P. barbatus": "384328",
    "P. canadensis": "388975",
    "P. dominula": "392813",
    "P. gracilis": "563886",
    "S. invicta": "394298",
    "T. cornetzi": "589138",
    "T. curvispinosus": "570444",
    "T. pretiosum": "808576",
    "T. septentrionalis": "608901",
    "T. zeteki": "614739",
    "V. emeryi": "619364",
    "W. auropunctata": "461204"
};

// Require bare bones jbrowse components without using the main browser object
require({
   packages: [
       'dbind',
       'dgrid',
       'dojo',
       'dijit',
       'dojox',
       'json-schema',
       'jszlib',
       { name: 'lazyload', main: 'lazyload' },
       'xstyle',
       'put-selector',
       { name: 'jDataView', location: 'jDataView/src', main: 'jdataview' },
       'FileSaver',
       'JBrowse'
   ]
},
[
   'dojo/cookie',
   'dojo/dom',
   'dojo/dom-construct',
   'dojo/dom-style',
   'dojo/dom-class',
   'JBrowse/Browser',
   'JBrowse/View/Track/HTMLFeatures',
   'JBrowse/Store/SeqFeature/NCList',
   'JBrowse/Model/SimpleFeature',
   'JBrowse/View/GranularRectLayout',
   'JBrowse/Store/Sequence/StaticChunked'
],
function (cookie,dom,domConstruct,domStyle,domClass,Browser,HTMLFeatures,NCList,SimpleFeature,Layout,StaticChunkedSequence) {
   var mymine = new intermine.Service({root: "https://hymenopteramine.rnet.missouri.edu/hymenopteramine"});

   var query = {
       from: 'Gene',
       select: [
           'chromosome.primaryIdentifier',
           'chromosome.assembly',
           'transcripts.chromosomeLocation.start',
           'transcripts.chromosomeLocation.end',
           'transcripts.chromosomeLocation.strand',
           'transcripts.exons.chromosomeLocation.start',
           'transcripts.exons.chromosomeLocation.end',
           'transcripts.primaryIdentifier',
           'transcripts.secondaryIdentifier',
           'transcripts.symbol',
           'organism.shortName'
       ],
       where: {
           primaryIdentifier: pid
       }
   };
   var createJBrowse=function(features,organism,assembly){
       var node=dom.byId("gene-structure-model");
       var height=15+Object.keys(features).length*31;
       domStyle.set(node,"height",height+'px');
       var trackConfig = {
          "style" : {
             "className" : "RefSeq",
             "subfeatureClasses" : {
                "wholeCDS" : null,
                "CodingSequence" : "RefSeq-CDS",
                "UTR" : "RefSeq-UTR"
             },
          },
          "storeClass" : "JBrowse/Store/SeqFeature/NCList",
          "type" : "FeatureTrack",
          "showLabels":false,
	  // comment out "onClick" below if multiple assemblies and uncomment code snippet below
          "onClick"  : {
              "label": "Feature name {name}\nFeature start {start}\nFeature end {end}",
              "url": "http://hymenopteragenome.org/Apollo2/" + organismMap[organism] + "/jbrowse/index.html?&loc={name}",
              "action": "newWindow"
          },
          "menuTemplate":null
       };

       // Uncomment if multiple assemblies:
       //if (assembly in assemblyMap) {
       //   trackConfig.onClick = {
       //       "label": "Feature name {name}\nFeature start {start}\nFeature end {end}",
       //       "url": "http://jbrowse-maizemine.rnet.missouri.edu:8080/apollo/" + assemblyMap[assembly] + "/jbrowse/index.html?loc={seq}:{start}..{end}",
       //       "action": "newWindow"
       //   }
       //}
       //else {
       //   trackConfig.onClick = {
       //       "label": "Feature name {name}\nFeature start {start}\nFeature end {end}"
       //   }
       //}

       // Fake existence of jbrowse object
       var browser=new Browser({unitTestMode: true});
       browser.view={};
       var mmin=1000000000;
       var mmax=-1000000000;

       for(feature in features) {
           if(features[feature].start<mmin) mmin=features[feature].start;
           if(features[feature].end>mmax) mmax=features[feature].end;
       }
       browser.view.minVisible=function() { return mmin; };
       browser.view.maxVisible=function() { return mmax; };
       var track = new HTMLFeatures({
           refSeq: "refseq",
           config: trackConfig,
           changeCallback: function() { console.log("changeCallback"); },
           store: NCList,
           browser: browser
       });
       // Fake existence of genomeview object
       track.genomeView=browser.view;

       // fake a Block in the BlockBased tracktype
       var block={};
       block.featureLayout = new Layout(mmin, mmax);
       block.featureNodes = new Array();
       block.startBase = mmin - (mmax-mmin) * 0.1;
       block.endBase = mmax + (mmax-mmin) * 0.1;
       block.domNode=dom.byId("display");


       // Manually add block to track
       track.blocks=[block];
       track.label=dom.byId("label");
       track.measureStyles();
       track.updateStaticElements({x:0,y:0,width:2000,height:height});

       for(feature in features) {
           var simplefeature=new SimpleFeature({data: features[feature]});
           var fmin=simplefeature.get('start');
           var fmax=simplefeature.get('end');
           // Force calculate some CSS related things
           var featNode=track.renderFeature(simplefeature,simplefeature.get("name"),block,0.02,0.01,0.01,block.startBase,block.endBase);


           if(featNode) block.domNode.appendChild(featNode);
           else continue;

           // Must call center children after adding to node
           track._centerChildrenVertically(featNode);
       }
       track.updateStaticElements({x:0,y:0,width:2000,height:height});
   }
   mymine.rows(query).then(function(rows) {
       var features={};
       var organism, assembly;
       rows.forEach(function printRow(row) {
           var transcript=row[7];
           if(!(transcript in features)) {
               features[transcript]={
                       "type":"mRNA",
		       "seq": row[0],
		       "assembly": row[1],
                       "start": row[2],
                       "end": row[3],
                       "strand": parseInt(row[4]),
                       "subfeatures":[],
                       "uniqueID":row[7],
                       "name":row[7]
                   };
           }
	   assembly=row[1];
           organism=row[10];
           features[transcript].subfeatures.push({
                   "start": row[5],
                   "end": row[6],
                   "type": "CodingSequence",
                   "strand": features[transcript].strand
                });
       });
       
       createJBrowse(features, organism, assembly);
   });
});
</script>

<div id="display" style="margin:15px;position:absolute;width:75%;"></div>
<div id="label"></div>

</div>

<!-- /geneStructureModel.jsp -->
