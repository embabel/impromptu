/*
 * Copyright 2024-2025 Embabel Software, Inc.
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
 */
package com.embabel.impromptu;

import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;

import java.util.List;
import java.util.Map;

/**
 * Helper class for creating test data in Neo4j.
 */
public class TestDataHelper {

    private final PersistenceManager persistenceManager;

    public TestDataHelper(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    /**
     * Creates sample composers for testing.
     */
    public void createComposers() {
        var composers = List.of(
                Map.of(
                        "id", "composer-beethoven",
                        "name", "Beethoven",
                        "completeName", "Ludwig van Beethoven",
                        "birthYear", 1770,
                        "deathYear", 1827,
                        "popular", true,
                        "recommended", true
                ),
                Map.of(
                        "id", "composer-mozart",
                        "name", "Mozart",
                        "completeName", "Wolfgang Amadeus Mozart",
                        "birthYear", 1756,
                        "deathYear", 1791,
                        "popular", true,
                        "recommended", true
                ),
                Map.of(
                        "id", "composer-bach",
                        "name", "Bach",
                        "completeName", "Johann Sebastian Bach",
                        "birthYear", 1685,
                        "deathYear", 1750,
                        "popular", true,
                        "recommended", true
                ),
                Map.of(
                        "id", "composer-chopin",
                        "name", "Chopin",
                        "completeName", "Frédéric Chopin",
                        "birthYear", 1810,
                        "deathYear", 1849,
                        "popular", true,
                        "recommended", false
                ),
                Map.of(
                        "id", "composer-brahms",
                        "name", "Brahms",
                        "completeName", "Johannes Brahms",
                        "birthYear", 1833,
                        "deathYear", 1897,
                        "popular", true,
                        "recommended", false
                )
        );

        persistenceManager.execute(
                QuerySpecification.withStatement("""
                        UNWIND $composers AS c
                        MERGE (composer:__Entity__:Composer:Reference {id: c.id})
                        SET composer.name = c.name,
                            composer.completeName = c.completeName,
                            composer.birthYear = c.birthYear,
                            composer.deathYear = c.deathYear,
                            composer.popular = c.popular,
                            composer.recommended = c.recommended,
                            composer.primarySource = "test"
                        """)
                        .bind(Map.of("composers", composers))
        );
    }

    /**
     * Creates sample works for testing.
     */
    public void createWorks() {
        var works = List.of(
                // Beethoven works
                Map.of(
                        "id", "work-beethoven-symphony5",
                        "composerId", "composer-beethoven",
                        "title", "Symphony No. 5",
                        "subtitle", "in C minor, Op. 67",
                        "description", "Beethoven's Symphony No. 5 in C minor, one of the most famous symphonies",
                        "popular", true,
                        "recommended", true
                ),
                Map.of(
                        "id", "work-beethoven-symphony9",
                        "composerId", "composer-beethoven",
                        "title", "Symphony No. 9",
                        "subtitle", "in D minor, Op. 125 'Choral'",
                        "description", "Beethoven's final complete symphony with the famous Ode to Joy",
                        "popular", true,
                        "recommended", true
                ),
                Map.of(
                        "id", "work-beethoven-moonlight",
                        "composerId", "composer-beethoven",
                        "title", "Piano Sonata No. 14",
                        "subtitle", "in C-sharp minor, Op. 27 No. 2 'Moonlight'",
                        "description", "The famous Moonlight Sonata by Beethoven",
                        "popular", true,
                        "recommended", true
                ),
                // Mozart works
                Map.of(
                        "id", "work-mozart-requiem",
                        "composerId", "composer-mozart",
                        "title", "Requiem",
                        "subtitle", "in D minor, K. 626",
                        "description", "Mozart's final, unfinished masterpiece",
                        "popular", true,
                        "recommended", true
                ),
                Map.of(
                        "id", "work-mozart-symphony40",
                        "composerId", "composer-mozart",
                        "title", "Symphony No. 40",
                        "subtitle", "in G minor, K. 550",
                        "description", "One of Mozart's most famous symphonies",
                        "popular", true,
                        "recommended", true
                ),
                // Bach works
                Map.of(
                        "id", "work-bach-welltempered1",
                        "composerId", "composer-bach",
                        "title", "The Well-Tempered Clavier",
                        "subtitle", "Book I, BWV 846-869",
                        "description", "Bach's collection of preludes and fugues in all major and minor keys",
                        "popular", true,
                        "recommended", true
                ),
                Map.of(
                        "id", "work-bach-toccata",
                        "composerId", "composer-bach",
                        "title", "Toccata and Fugue",
                        "subtitle", "in D minor, BWV 565",
                        "description", "One of the most famous organ works",
                        "popular", true,
                        "recommended", true
                ),
                // Chopin works
                Map.of(
                        "id", "work-chopin-ballade1",
                        "composerId", "composer-chopin",
                        "title", "Ballade No. 1",
                        "subtitle", "in G minor, Op. 23",
                        "description", "Chopin's first ballade, a romantic masterpiece for piano",
                        "popular", true,
                        "recommended", false
                ),
                // Brahms works
                Map.of(
                        "id", "work-brahms-symphony4",
                        "composerId", "composer-brahms",
                        "title", "Symphony No. 4",
                        "subtitle", "in E minor, Op. 98",
                        "description", "Brahms' final symphony",
                        "popular", true,
                        "recommended", false
                )
        );

        persistenceManager.execute(
                QuerySpecification.withStatement("""
                        UNWIND $works AS w
                        MATCH (composer:__Entity__:Composer:Reference {id: w.composerId})
                        MERGE (work:__Entity__:Work:Reference {id: w.id})
                        SET work.name = w.title,
                            work.title = w.title,
                            work.subtitle = w.subtitle,
                            work.description = w.description,
                            work.popular = w.popular,
                            work.recommended = w.recommended,
                            work.primarySource = "test"
                        MERGE (composer)-[:COMPOSED]->(work)
                        """)
                        .bind(Map.of("works", works))
        );
    }

    /**
     * Creates all sample test data (composers and works).
     */
    public void createAllTestData() {
        createComposers();
        createWorks();
    }

    /**
     * Clears all test data.
     */
    public void clearTestData() {
        persistenceManager.execute(
                QuerySpecification.withStatement(
                        "MATCH (n) WHERE n.primarySource = 'test' DETACH DELETE n"
                )
        );
    }

    /**
     * Gets the count of composers in the database.
     */
    public long countComposers() {
        return persistenceManager.getOne(
                QuerySpecification.withStatement("MATCH (c:Composer) RETURN count(c)")
                        .transform(Long.class)
        );
    }

    /**
     * Gets the count of works in the database.
     */
    public long countWorks() {
        return persistenceManager.getOne(
                QuerySpecification.withStatement("MATCH (w:Work) RETURN count(w)")
                        .transform(Long.class)
        );
    }
}
