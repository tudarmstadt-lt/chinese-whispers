package de.tudarmstadt.lt.wsi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import de.tudarmstadt.lt.util.IndexUtil;
import de.tudarmstadt.lt.util.IndexUtil.Index;
import de.tudarmstadt.lt.util.MapUtil;

public class ClusterReaderWriter {
	final static Charset UTF_8 = Charset.forName("UTF-8");


	public static void writeClusters(Writer writer, Map<String, List<Cluster<String>>> clusters) throws IOException {
		writeClusters(writer, clusters, IndexUtil.<String>getIdentityIndex());
	}
	
	public static <N> void writeClusters(Writer writer, Map<String, List<Cluster<N>>> clusters, Index<String, N> index) throws IOException {
		for (Entry<String, List<Cluster<N>>> clusterList : clusters.entrySet()) {
			for (Cluster<N> c : clusterList.getValue()) {
				writeCluster(writer, c, index);
			}
		}
	}

	public static void writeCluster(Writer writer, Cluster<String> cluster) throws IOException {
		writeCluster(writer, cluster, IndexUtil.<String>getIdentityIndex());
	}

	public static <N> void writeCluster(Writer writer, Cluster<N> cluster, Index<String, N> index) throws IOException {
		writer.write(cluster.name + "\t" + cluster.clusterId + "\t" + cluster.label + "\t");
		writer.write(StringUtils.join(IndexUtil.map(cluster.nodes, index), "  "));
		if (!cluster.featureScores.isEmpty()) {
			writer.write("\t");
			Map<N, Float> sortedFeatureCounts = MapUtil.sortMapByValue(cluster.featureScores);
			MapUtil.writeMap(IndexUtil.mapKeys(sortedFeatureCounts, index), writer, ":", "  ");
		}
		writer.write("\n");
	}

	public static Map<String, List<Cluster<String>>> readClusters(Reader in) throws IOException {
		return readClusters(in, IndexUtil.<String>getIdentityIndex(), null);
	}
	
	public static <N> Map<N, List<Cluster<N>>> readClusters(Reader in, Index<String, N> index, Set<String> whitelist) throws IOException {
		System.out.println("Reading clusters...");
		Map<N, List<Cluster<N>>> clusters = new HashMap<N, List<Cluster<N>>>();
		
		BufferedReader reader = new BufferedReader(in);
		String line;
		while ((line = reader.readLine()) != null) {
			String[] lineSplits = line.split("\t");
			if (whitelist != null && !whitelist.contains(lineSplits[0])) {
				continue;
			}
			N clusterName = index.getIndex(lineSplits[0]);
			int clusterId = Integer.parseInt(lineSplits[1]);
			N clusterLabel = index.getIndex(lineSplits[2]);
			String[] clusterNodes = lineSplits[3].split("  ");
			Set<N> clusterNodeSet = new HashSet<N>(5);
			// TODO: replace by IndexUtil.map ...
			for (String clusterNode : clusterNodes) {
				if (!clusterNode.isEmpty()) {
					clusterNodeSet.add(index.getIndex(clusterNode));
				}
			}
			Map<N, Float> clusterFeatureProbs = new HashMap<N, Float>();
			Map<N, Float> clusterFeatureScores = new HashMap<N, Float>();
			if (lineSplits.length >= 5) {
				String[] clusterFeatures = lineSplits[4].split("  ");
				final int MAX_NUM_FEATURES = Integer.MAX_VALUE;
				int i = 0;
				for (String featureScorePair : clusterFeatures) {
					if (i >= MAX_NUM_FEATURES) {
						break;
					}
					String[] featureArr = featureScorePair.split(":");
					// TODO: remove isEmpty() check
					if (featureArr.length == 3) {
						try {
							N feature = index.getIndex(featureArr[0]);
							Float prob = Float.parseFloat(featureArr[1]);
							Float coverage = Float.parseFloat(featureArr[2]);
//							if (coverage*prob > 0.00001 && coverage > 0.0001) {
								clusterFeatureProbs.put(feature, prob);
								clusterFeatureScores.put(feature, prob*coverage);
//							}
						} catch (NumberFormatException e) {
							System.err.println("Error (1): malformatted feature-count pair: " + featureScorePair);
						}
					} else {
						System.err.println("Error (2): malformatted feature-count pair: " + featureScorePair);
					}
					i++;
				}
			}
			List<N> clusterFeaturesSorted = MapUtil.sortMapKeysByValue(clusterFeatureScores);
			Map<N, Float> clusterFeatureProbsFiltered = new HashMap<N, Float>();
			int i = 0;
			int limit = 600;
			for(N feature : clusterFeaturesSorted) {
				if (i >= limit) {
					break;
				}
				clusterFeatureProbsFiltered.put(feature, clusterFeatureProbs.get(feature));
				i++;
			}
			Cluster<N> c = new Cluster<N>(clusterName, clusterId, clusterLabel, clusterNodeSet, clusterFeatureProbsFiltered);
			MapUtil.addTo(clusters, clusterName, c, ArrayList.class);
		}
		return clusters;
	}
	
	public static <N> Map<N, String> readBaselineMapping(Reader in, Index<String, N> index, Set<String> whitelist, Map<N, List<Cluster<N>>> clusters) throws IOException {
		Map<N, String> mapping = new HashMap<N, String>();
		
		BufferedReader reader = new BufferedReader(in);
		String line;
		while ((line = reader.readLine()) != null) {
			String[] lineSplits = line.split("\t");
			if (whitelist != null && !whitelist.contains(lineSplits[0])) {
				continue;
			}
			N jo = index.getIndex(lineSplits[0]);
			String[] resources = lineSplits[1].split("  ");
			String resource = resources[0].split(":")[0];
			
			mapping.put(jo, resource);
		}
		
		return mapping;
	}
	
	public static <N> Map<Cluster<N>, String> readClusterMapping(Reader in, Index<String, N> index, Set<String> whitelist, Map<N, List<Cluster<N>>> clusters) throws IOException {
		Map<Cluster<N>, String> mapping = new HashMap<Cluster<N>, String>();
		
		BufferedReader reader = new BufferedReader(in);
		String line;
		while ((line = reader.readLine()) != null) {
			String[] lineSplits = line.split("\t");
			if (whitelist != null && !whitelist.contains(lineSplits[0])) {
				continue;
			}
			N jo = index.getIndex(lineSplits[0]);
			int clusterId = Integer.parseInt(lineSplits[1]);
			String[] resources = lineSplits[2].split("  ");
			String resource = resources[0].split(":")[0];
			
			Cluster<N> sense = null;
			List<Cluster<N>> clusterSet = clusters.get(jo);
			for (Cluster<N> c : clusterSet) {
				if (c.clusterId == clusterId) {
					sense = c;
					break;
				}
			}
			
			mapping.put(sense, resource);
		}
		
		return mapping;
	}
}
