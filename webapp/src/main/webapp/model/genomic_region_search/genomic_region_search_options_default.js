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
                        '<p id="selectFeatureTypes" style="padding-bottom:8px;"></p>' +
                        '<table id="featureTypes" cellpadding="0" cellspacing="0" border="0">' +
                        '</table>' +
                        '</li>' +
                        '<br>';

        jQuery(htmlToInsert).insertBefore('#genomicRegionInput');

        // when organism changes, the feature types will change accordingly
        jQuery("#organisms").change(function () {
            setInputOptions();
        })
        .trigger('change');

        window.addEventListener("pageshow", () => {
            // Should reset when back button is pressed
            setInputOptions();
        });
    });

    function setInputOptions() {
        // Reset textarea and file input
        resetInputs();

        jQuery("#organisms option:selected").each(function () {
            // Not used: Update genome build dropdown
            //appendGenomeBuild(jQuery(this).val());

            // Update feature types checkboxes
            appendFeatureTypes(jQuery(this).val());

            // Update assembly dropdown, if using
            if (useAssemblyFilter) {
                appendAssemblyVersions(jQuery(this).val());
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

    function appendFeatureTypes(org) {
        var featureTypes = jQuery("#featureTypes").empty(),
            row = "<tr></tr>",
            input = "<input type='checkbox' class='featureType' name='featureTypes'>",
            cell = "<td width='300'></td>",
            br = "<br/>",
            sp = "&nbsp;",
            onClick = function() {uncheck(this.checked, 'featureTypes')},
            columns = 3;

         for (var i in webDataJSON.featureTypes){
               if (webDataJSON.featureTypes[i].organism == org) {
                     var feature_size = webDataJSON.featureTypes[i].features.length,
                         rows = Math.ceil(feature_size/columns);

                     for (j = 0; j < rows; j++) {
                        var rowElem = jQuery(row);
                        for (k = 0; k < columns; k++) {
                            var current_loc = j + k*rows;
                            if (!(current_loc >= feature_size)) {
                                var current = webDataJSON.featureTypes[i].features[current_loc].featureType;
                                var displayName = $MODEL_TRANSLATION_TABLE[current].displayName ? $MODEL_TRANSLATION_TABLE[current].displayName : current;
                                var description = webDataJSON.featureTypes[i].features[current_loc].description;
                                var desBox = "<a onclick=\"document.getElementById('ctxHelpTxt').innerHTML='" + displayName + ": " + description.replace(/&apos;/g, "\\'")
                                             + "';document.getElementById('ctxHelpDiv').style.display=''; window.scrollTo(0, 0);return false\" title=\"" + description
                                             + "\"><img class=\"tinyQuestionMark\" src=\"images/icons/information-small-blue.png\" alt=\"?\" style=\"padding: 4px 3px\"></a>"
                                var cellElem = jQuery(cell);
                                var ckbx = jQuery(input).attr("value", current).click(onClick);
                                cellElem.append(ckbx).append(sp).append(displayName).append(desBox);
                                rowElem.append(cellElem);
                            }
                        }
                        featureTypes.append(rowElem);
                    }
               }
         }

         if (featureTypes.children.length) {
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
