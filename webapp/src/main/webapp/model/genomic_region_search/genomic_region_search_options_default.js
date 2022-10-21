//=== A mine specific script ===
//=== This is the default script for generic purpose ===

    jQuery(document).ready(function() {
        var htmlToInsert = '<li>' +
                           '<span>Select Organism:&nbsp;</span>' +
                           '<select id="organisms" name="organism">';

        // create organism dropdown
        if (useFullOrgNames) {
            // display organism full names
            // iterate through this one instead of 'fullnames' because order matters,
            jQuery.each(webDataJSON.organisms, function() {
                for (i in webDataJSON.fullnames) {
                    if (webDataJSON.fullnames[i].organism == this) {
                        htmlToInsert += '<option value="'+this+'">'+webDataJSON.fullnames[i].fullname+'</option>';
                        break;
                    }
                }
            });
        } else {
            // display organism short names
            jQuery.each(webDataJSON.organisms, function() {
                htmlToInsert += '<option value="'+this+'">'+this+'</option>';
            });
        }

        //htmlToInsert += '</select>' + '<span id="genomeBuild" style="padding:10px;"></span>'
        //                '</li><br>';
        htmlToInsert += '</select></li><br>';

        // create assembly dropdown, if using
        if (useAssemblyFilter) {
            htmlToInsert += '<li><span>Select Assembly:&nbsp;</span>' +
                            '<select id="assembly" name="assembly">';
            htmlToInsert += '</select></li><br>';
        }

        htmlToInsert += '<li>' +
                        '<span id="selectFeatureTypes" style="padding-bottom:8px;"></span>' +
                        '<table id="featureTypes" cellpadding="0" cellspacing="0" border="0">' +
                        '</table>' +
                        '</li>' +
                        '<br>';

        jQuery(htmlToInsert).insertBefore('#genomicRegionInput');

        // when organism changes, the feature types will change accordingly
        jQuery("#organisms").change(function () {
            setInputOptions();
        })

        if (useAssemblyFilter) {
            // When assembly changes, update features checkboxes
            jQuery("#assembly").change(function () {
                appendFeatureTypes(jQuery('#organisms').val(), true);
            });
        }

        window.addEventListener("pageshow", () => {
            // Should reset when back button is pressed
            setInputOptions();
        });

        // Initial call of setInputOptions:
        setInputOptions();
    });

    function setInputOptions() {
        // Reset textarea and file input
        resetInputs();

        jQuery("#organisms option:selected").each(function () {
            // Not used: Update genome build dropdown
            //appendGenomeBuild(jQuery(this).val());

            // Update assembly dropdown, if using
            if (useAssemblyFilter) {
                appendAssemblyVersions(jQuery(this).val());
            } else {
                // Update feature types checkboxes based on selected organism
                appendFeatureTypes(jQuery(this).val(), false);
            }
        });
    }

    //function appendGenomeBuild(org) {
    //    for(i in webDataJSON.genomeBuilds){
    //        if (webDataJSON.genomeBuilds[i].organism == org) {
    //            jQuery("#genomeBuild").html("<i>genome build: <span id='current-genome-version'>" + webDataJSON.genomeBuilds[i].genomeBuild + "</span></i>");
    //        }
    //    }
    //}

    function appendFeatureTypes(org, filterByAssembly) {
        var featureTypes = jQuery("#featureTypes").empty(),
            row = "<tr></tr>",
            input = "<input type='checkbox' class='featureType' name='featureTypes'>",
            cell = "<td width='300'></td>",
            br = "<br/>",
            sp = "&nbsp;",
            onClick = function() {uncheck(this.checked, 'featureTypes')},
            columns = 3;

        // 1. Use webDataJSON.featureTypes to build list of features as usual with their descriptions
        // 2. If filtering by assembly, use selected assembly version to filter feature type list

        // Get all possible features for selected organism
        var allFeaturesList = [];
        for (i in webDataJSON.featureTypes) {
            if (webDataJSON.featureTypes[i].organism == org) {
                var featureLen = webDataJSON.featureTypes[i].features.length;
                for (var j=0; j<featureLen; j++) {
                    try {
                        var cur = webDataJSON.featureTypes[i].features[j].featureType;
                        var dispName = $MODEL_TRANSLATION_TABLE[cur].displayName ? $MODEL_TRANSLATION_TABLE[cur].displayName : cur;
                        var desc = webDataJSON.featureTypes[i].features[j].description;
                        var cellData = {current: cur, displayName: dispName, description: desc};
                        allFeaturesList.push(cellData);
                    } catch(e) {
                        console.log("$MODEL_TRANSLATION_TABLE does not have attribute:", cur);
                    }
                }
                break;
            }
        }

        if (filterByAssembly) {
            // Get selected assembly
            var assembly = jQuery('#assembly').val();

            // Get the list of features available from selected assembly version
            var selectedAssemblyFeaturesList = [];
            for (var i in webDataJSON.assemblies_features) {
                if (webDataJSON.assemblies_features[i].organism == org) {
                    for (var j in webDataJSON.assemblies_features[i].assemblies) {
                        if (webDataJSON.assemblies_features[i].assemblies[j].assembly == assembly) {
                            selectedAssemblyFeaturesList = webDataJSON.assemblies_features[i].assemblies[j].features;
                            break;
                        }
                    }
                    break;
                }
            }

            // Create the final feature list from those selected
            var featureList = [];
            for (i=0; i < allFeaturesList.length; ++i) {
                if (selectedAssemblyFeaturesList.indexOf(allFeaturesList[i].current) >= 0) {
                    featureList.push(allFeaturesList[i]);
                }
            }

            // Update all features list to final version
            allFeaturesList = featureList;
        }

        // Now build the table as usual
        var featureSize = allFeaturesList.length;
        var rows = Math.ceil(featureSize/columns);
        var i=0;

        if (featureSize > 0) {
            for (j = 0; j < rows; j++) {
                var rowElem = jQuery(row);
                for (k = 0; k < columns; k++) {
                    // Since may not have equal number of rows and columns, exit early once we've
                    // exhausted feature list
                    if (i >= featureSize) {
                        break;
                    }

                    var current = allFeaturesList[i].current,
                        displayName = allFeaturesList[i].displayName,
                        description = allFeaturesList[i].description;

                    var desBox = "<a onclick=\"document.getElementById('ctxHelpTxt').innerHTML='"
                               + displayName + ": " + description.replace(/&apos;/g, "\\'")
                               + "';document.getElementById('ctxHelpDiv').style.display=''; window.scrollTo(0, 0);"
                               + "return false\" title=\"" + description + "\">"
                               + "<img class=\"tinyQuestionMark\" src=\"images/icons/information-small-blue.png\""
                               + "alt=\"?\" style=\"padding: 4px 3px\"></a>";
                    var cellElem = jQuery(cell);
                    var onClick = function() {uncheck(this.checked)};
                    var ckbx = jQuery(input).attr({value: current}).click(onClick);
                    cellElem.append(ckbx).append(sp).append(displayName).append(desBox);
                    rowElem.append(cellElem);
                     i++; // proceed to next feature in list
                }
                featureTypes.append(rowElem);
            }
            jQuery("#selectFeatureTypes").html("<input id=\"check\" type=\"checkbox\" onclick=\"checkAll(this.id)\"/>&nbsp;Select Feature Types:");
        } else {
            jQuery("#selectFeatureTypes").html("Select Feature Types:<br><i>"+org+" does not have any features</i>");
        }
    }

    function appendAssemblyVersions(org) {
        jQuery("#assembly").empty();
        for (var i = 0; i < webDataJSON.assemblies.size(); i++) {
            if (webDataJSON.assemblies[i].organism == org) {
                var assemblies = webDataJSON.assemblies[i].assembly.sort();
                for (var j = 0; j < assemblies.size(); j++) {
                    jQuery("#assembly").append("<option value='" + assemblies[j] + "'>" + assemblies[j] + "</option>");
                }
            }
        }
        // Add feature checkboxes based on selected assembly version
        appendFeatureTypes(org, true);
    }

    // (un)Check all featureType checkboxes
    function checkAll(id) {
        jQuery(".featureType").prop('checked', jQuery('#' + id).is(':checked'));
        jQuery("#check").css("opacity", 1);
    }

    // check/uncheck any featureType checkbox
    function uncheck(status, name)
    {
         var statTag;
         if (!status) { //unchecked
           jQuery(".featureType").each(function() {
             if (this.checked) {statTag=true;}
           });

           if (statTag) {
            jQuery("#check").prop('checked', true);
            jQuery("#check").css("opacity", 0.5); }
           else {
            jQuery("#check").removeAttr('checked');
            jQuery("#check").css("opacity", 1);}
         }
         else { //checked
           jQuery(".featureType").each(function() {
             if (!this.checked) {statTag=true;}
         });

         if (statTag) {
           jQuery("#check").prop('checked', true);
           jQuery("#check").css("opacity", 0.5); }
         else {
           jQuery("#check").prop('checked', true);
           jQuery("#check").css("opacity", 1);}
         }
    }

    function validateBeforeSubmit() {
       var checkedFeatureTypes = [];
       jQuery(".featureType").each(function() {
           if (this.checked) { checkedFeatureTypes.push(this.value); }
       });
       var checkedFeatureTypesToString = checkedFeatureTypes.join(",");

       if (jQuery(".featureType").val() == null || checkedFeatureTypesToString == "") {
           alert("No feature types selected. At least one feature type is required.");
           return false;
       }

       if (jQuery("#pasteInput").val() == "" && jQuery("#fileInput").val() == "") {
           alert("No genome regions entered. Type/paste/upload some genome regions.");
           return false;
       }

       if (jQuery("#pasteInput").val() != "") {
             // Regex validation
             var ddotsRegex = /^[^:\t\s]+: ?\d+(,\d+)*\.\.\d+(,\d+)*$/;
             var tabRegex = /^[^\t\s]+(\t\d+(,\d+)*){2}/; // this will match the line start with
             var dashRegex = /^[^:\t\s]+: ?\d+(,\d+)*\-\d+(,\d+)*$/;
             var snpRegex = /^[^:\t\s]+: ?\d+(,\d+)*$/;
             var emptyLine = /^\s*$/;
             var ddotstagRegex = /^[^:]+: ?\d+(,\d+)*\.\.\d+(,\d+)*: ?\d+$/;

             var spanArray = jQuery.trim(jQuery("#pasteInput").val()).split("\n");
             var lineNum;
             for (i=0;i<spanArray.length;i++) {
               lineNum = i + 1;
               if (spanArray[i] == "") {
                   alert("Line " + lineNum + " is empty.");
                   return false;
               }
               if (!spanArray[i].match(ddotsRegex) &&
                   !spanArray[i].match(ddotstagRegex) &&
                   !spanArray[i].match(tabRegex) &&
                   !spanArray[i].match(dashRegex) &&
                   !spanArray[i].match(snpRegex) &&
                   !spanArray[i].match(emptyLine)
                   ) {
                      alert(spanArray[i] + " doesn't match any supported format.");
                      return false;
               }
               if (spanArray[i].match(ddotsRegex)) {
                   var start = parseInt(spanArray[i].split(":")[1].split("..")[0].replace(/\,/g,''));
                   var end = parseInt(spanArray[i].split(":")[1].split("..")[1].replace(/\,/g,''));
               }
               if (spanArray[i].match(tabRegex)) {
                   var start = parseInt(spanArray[i].split("\t")[1].replace(/\,/g,''));
                   var end = parseInt(spanArray[i].split("\t")[2].replace(/\,/g,''));
               }
               if (spanArray[i].match(dashRegex)) {
                   var start = parseInt(spanArray[i].split(":")[1].split("-")[0].replace(/\,/g,''));
                   var end = parseInt(spanArray[i].split(":")[1].split("-")[1].replace(/\,/g,''));
               }
         }
       }
       return true;
    }

    // Switch organism to the one that matches the examples
    function loadOrganism() {
        jQuery("#organisms").val(exampleOrganism).change();
    }

    // Switch assembly to the one that matches the examples
    function loadAssembly() {
        jQuery("#assembly").val(exampleAssembly).change();
    }

    // Load example from web.properties file
    function loadExample(exampleSpans) {
        // Changing back to original behavior (don't reset org dropdown)
        //loadOrganism();
        //if (useAssemblyFilter) {
        //    loadAssembly();
        //}
        switchInputs('paste','file');
        jQuery('#pasteInput').focus();
        jQuery('#pasteInput').val(exampleSpans);
    }

    function loadExample1() {
        loadExample(exampleSpansFormat1);
        return false;
    }

    function loadExample2() {
        loadExample(exampleSpansFormat2);
        return false;
    }

    function loadExample3() {
        loadExample(exampleSpansFormat3);
        return false;
    }
