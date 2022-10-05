#!/usr/bin/bash
# "Setting MSYS2_ARG_CONV_EXCL so that Trash XPaths do not get mutulated."
export MSYS2_ARG_CONV_EXCL="*"
where=`dirname -- "$0"`
cd "$where"
where=`pwd`
if [ ! -d Generated ]
then
	mkdir Generated > /dev/null 2> /dev/null
	cd Generated > /dev/null 2> /dev/null
	git clone https://github.com/antlr/grammars-v4.git > /dev/null 2> /dev/null
	cd .. > /dev/null 2> /dev/null
fi
cd Generated/grammars-v4
cat -<<EOF
var grammars_v4 = [
EOF
for i in `find . -name pom.xml`
do
    base=`dirname $i | sed 's%^./%%'`
    grep -qs . $base/*.g4
    if [ "$?" = "0" ]
    then
	if [ ! -d $base/examples ]
	then
		continue
	fi
	if [ "$base" == "kotlin/kotlin" ]
	then
		continue;
	fi
	if [ "$base" == "r" ]
	then
		continue;
	fi
	if [ "$base" == "sql/hive/v2" ]
	then
		continue;
	fi
	if [ "$base" == "sql/hive/v3" ]
	then
		continue;
	fi
	if [ "$base" == "stringtemplate" ]
	then
		continue;
	fi
	if [ "$base" == "verilog/systemverilog" ]
	then
		continue;
	fi
	if [ "$base" == "verilog/verilog" ]
	then
		continue;
	fi
	if [ "$base" == "z" ]
	then
		continue;
	fi
	if [ `trxml2 pom.xml | grep -E -e 'include=.*Parser.g4' | wc -l` -gt 1 ]
	then
		continue
	fi

	lexer=`trxml2 $i | grep -E -e 'include=.*Lexer.g4' | sed 's/[^=]*=//'`
	if [ "$lexer" != "" ]
	then
		lexer="https://raw.githubusercontent.com/antlr/grammars-v4/master/$base/$lexer"
	fi
	parser=`trxml2 $i | grep -E -e 'include=.*Parser.g4' | grep -v PreprocessorParser | sed 's/[^=]*=//'`
	if [ "$parser" != "" ]
	then
		parser="https://raw.githubusercontent.com/antlr/grammars-v4/master/$base/$parser"
	fi
	if [ "$lexer" == "" ] && [ "$parser" == "" ]
	then
		parser=`trxml2 $i | grep -E -e 'include=.*.g4' | grep -v Cobol85Preprocessor | sed 's/[^=]*=//'`
		parser="https://raw.githubusercontent.com/antlr/grammars-v4/master/$base/$parser"
	fi
	start=`trxml2 $i | grep -E -e 'entryPoint=' | sed 's/[^=]*=//' | sort -u`
	examples=`find $base/examples -type f`
	new_examples=""
	cat -<<EOF
	{
		name: "$base",
		lexer: "$lexer",
		parser: "$parser",
		start: "$start",
		example: [
EOF
	for e in $examples
	do
		echo "\
			'https://raw.githubusercontent.com/antlr/grammars-v4/master/$e',"
	done
	cat -<<EOF
		]
	},
EOF
    fi
done
cat -<<EOF
];
EOF
