# Geode Demo

### how to run
```
gradle task:

getTar(down load geode tgz)
installGeode(unzip)
cleanWorkingDir
start
stop

```

## simpleIndex uses default Lucene StandardAnalyzer
create lucene index --name=simpleIndex --region=EmployeeData --field=firstName,lastName

## analyzerIndex uses both the default StandardAnalyzer and the KeywordAnalyzer
create lucene index --name=analyzerIndex --region=EmployeeData --field=lastName,email --analyzer=DEFAULT,org.apache.lucene.analysis.core.KeywordAnalyzer

## nestedObjectIndex will index on nested objects or collection objects
create lucene index --name=nestedObjectIndex --region=EmployeeData --field=contacts.phoneNumbers --serializer=org.apache.geode.cache.lucene.FlatFormatSerializer

create region --name=example-region --type=PARTITION --enable-statistics=true



create region --name=EmployeeData --type=REPLICATE --skip-if-exists=true
create index --name=topLevelIndex --expression=name --region=/EmployeeData
create index --name=nestedIndex --expression=flight.airlineCode --region=/EmployeeData




```