package grakn.biograkn.precisionmedicine.migrator.genedisease;

import grakn.biograkn.utils.Utils;
import grakn.client.GraknClient;import graql.lang.Graql;
import graql.lang.query.GraqlInsert;
import graql.lang.query.GraqlGet;
import grakn.core.concept.answer.ConceptMap;

import static graql.lang.Graql.var;

import grakn.biograkn.precisionmedicine.migrator.disease.Disease;
import grakn.biograkn.precisionmedicine.migrator.gene.Gene;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@SuppressWarnings("Duplicates")
public class GeneDiseaseAssociation {
    public static void migrate(GraknClient.Session session) {
        System.out.print("\tMigrating Gene Disease Associations");

        migrateFromDisgenet(session);
        migrateFromClinvar(session);

        System.out.println(" - [DONE]");
    }

    private static void migrateFromDisgenet(GraknClient.Session session) {
        try {
            BufferedReader reader = Files.newBufferedReader(Paths.get("precisionmedicine/dataset/disgenet/curated_gene_disease_associations.csv"));
            CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT);

            List<GraqlInsert> insertQueries = new ArrayList<>();

            for (CSVRecord csvRecord: csvParser) {

                // skip header
                if (csvRecord.getRecordNumber() == 1) {
                    continue;
                }

                String symbol = csvRecord.get(1);
                String diseaseId = csvRecord.get(4);
                double score = Double.parseDouble(csvRecord.get(9));

                GraqlInsert graqlInsert = Graql.match(
                        var("g").isa("gene").has("symbol", symbol),
                        var("d").isa("disease").has("disease-id", diseaseId))
                        .insert(var("gda").isa("gene-disease-association").rel("associated-gene", "g").rel("associated-disease", "d").has("score", score));


                // if (insertedIds.isEmpty()) {
                //     List<Class> prereqs = Arrays.asList(Gene.class, Disease.class);
                //     throw new IllegalStateException("Nothing was inserted for: " + GraqlInsert.toString() +
                //             "\nA prerequisite dataset may have not been loaded. This dataset requires: " + prereqs.toString());
                // }

                insertQueries.add(graqlInsert);
            }
            Utils.executeQueriesConcurrently(session, insertQueries);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void migrateFromClinvar(GraknClient.Session session) {
        try {
            BufferedReader reader = Files.newBufferedReader(Paths.get("precisionmedicine/dataset/clinvar/gene_condition_source_id.csv"));
            CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT);

            List<GraqlInsert> insertQueries = new ArrayList<>();

            for (CSVRecord csvRecord: csvParser) {

                // skip header
                if (csvRecord.getRecordNumber() == 1) {
                    continue;
                }

                String symbol = csvRecord.get(1);
                String diseaseId = csvRecord.get(3);

                GraknClient.Transaction readTransaction = session.transaction().read();

                GraqlGet graqlGet = Graql.match(
                        var("g").isa("gene").has("symbol", symbol),
                        var("d").isa("disease").has("disease-id", diseaseId),
                        var("gda").isa("gene-disease-association").rel("associated-gene", "g").rel("associated-disease", "d")).get();


                List<ConceptMap> getIds = readTransaction.execute(graqlGet);
                readTransaction.close();

//                if relationship does not exist already create it
                if(getIds.size() == 0) {
                    GraqlInsert graqlInsert =  Graql.match(
                            var("g").isa("gene").has("symbol", symbol),
                            var("d").isa("disease").has("disease-id", diseaseId))
                            .insert(var("gda").isa("gene-disease-association").rel("associated-gene", "g").rel("associated-disease", "d"));

                    // if (insertedIds.isEmpty()) {
                    //     List<Class> prereqs = Arrays.asList(Gene.class, Disease.class);
                    //     throw new IllegalStateException("Nothing was inserted for: " + GraqlInsert.toString() +
                    //             "\nA prerequisite dataset may have not been loaded. This dataset requires: " + prereqs.toString());
                    // }

                    insertQueries.add(graqlInsert);
                }
            }
            Utils.executeQueriesConcurrently(session, insertQueries);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
