/*
 * Copyright 2013-2014 Mikhail Shugay (mikhail.shugay@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Last modified on 29.11.2014 by mikesh
 */

package com.antigenomics.vdjtools.pwm

import com.antigenomics.vdjtools.Software
import com.antigenomics.vdjtools.sample.Sample
import com.antigenomics.vdjtools.sample.SampleCollection

import static com.antigenomics.vdjtools.util.ExecUtil.formOutputPath


def cli = new CliBuilder(usage: "ComputePwms [options] " +
        "[sample1 sample2 sample3 ... if -m is not specified] output_prefix")
cli.h("display help message")
cli.S(longOpt: "software", argName: "string", required: true, args: 1,
        "Software used to process RepSeq data. Currently supported: ${Software.values().join(", ")}")
cli.m(longOpt: "metadata", argName: "filename", args: 1,
        "Metadata file. First and second columns should contain file name and sample id. " +
                "Header is mandatory and will be used to assign column names for metadata.")
cli.p(longOpt: "plot", "Plot matrices with PWMs")
cli.f(longOpt: "factor", argName: "string", args: 1, "Metadata entry used to split samples. " +
        "Factor values will be interpreted as a discrete set.")

def opt = cli.parse(args)

if (opt == null)
    System.exit(-1)

if (opt.h || opt.arguments().size() == 0) {
    cli.usage()
    System.exit(-1)
}

// Check if metadata is provided

def metadataFileName = opt.m

if (metadataFileName ? opt.arguments().size() != 1 : opt.arguments().size() < 2) {
    if (metadataFileName)
        println "Only output prefix should be provided in case of -m"
    else
        println "At least 1 sample files should be provided if not using -m"
    cli.usage()
    System.exit(-1)
}

def software = Software.byName(opt.S),
    outputPrefix = opt.arguments()[-1],
    factor = (String) (opt.f ?: null),
    plot = (boolean) opt.p

def scriptName = getClass().canonicalName.split("\\.")[-1]

//
// Batch load all samples (lazy)
//

println "[${new Date()} $scriptName] Reading samples"

def sampleCollection = metadataFileName ?
        new SampleCollection((String) metadataFileName, software) :
        new SampleCollection(opt.arguments()[0..-2], software)
def metadataTable = sampleCollection.metadataTable

println "[${new Date()} $scriptName] ${sampleCollection.size()} samples loaded"

//
// Check factor exists
//

if (factor) {
    def factorCol = metadataTable.getColumn(factor)
    if (!factorCol) {
        println "[ERROR] Factor $factor does not exist in metadata, possible factors:\n" +
                "${metadataTable.columnHeader}"
        System.exit(-1)
    }
}

//
// Split samples by factor and group within CdrPwmGrids
//

def pwmGridMap = new HashMap<String, CdrPwmGrid>()
sampleCollection.each { Sample sample ->
    def factorName = factor ? sample.sampleMetadata[factor] : "all"

    def pwmGrid = pwmGridMap[factorName]
    if (!pwmGrid)
        pwmGridMap.put(factorName, pwmGrid = new CdrPwmGrid())

    pwmGrid.update(sample)
}

//
// Report those pwms
//

pwmGridMap.each {
    new File(formOutputPath(outputPrefix, "pwmgrid", it.key)).withPrintWriter { pw ->
        pw.println(CdrPwmGrid.HEADER)
        pw.println(it.value)
    }
}