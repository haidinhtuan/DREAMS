"use client";

import React, { useEffect, useRef, useState } from "react";
import CytoscapeComponent from "react-cytoscapejs";
import cytoscape from "cytoscape";
import fcose from "cytoscape-fcose";

// Register the fcose layout
cytoscape.use(fcose);

// Extend LayoutOptions to include fcose-specific properties
interface FCOSELayoutOptions extends cytoscape.BaseLayoutOptions {
  name: "fcose";
  randomize: boolean;
  fit: boolean;
  padding: number;
  nodeRepulsion: number;
  idealEdgeLength: number;
  edgeElasticity: number;
}

interface NodeData {
  id: string;
  label: string;
  clusterId: string;
  location: string;
}

interface EdgeData {
  source: string;
  target: string;
  weight: number;
}

interface GraphData {
  nodes: { data: NodeData }[];
  edges: { data: EdgeData }[];
}

const Graph: React.FC = () => {
  const [elements, setElements] = useState<cytoscape.ElementDefinition[]>([]);
  const cyRef = useRef<cytoscape.Core | null>(null); // Reference to Cytoscape instance

  useEffect(() => {
    const ws = new WebSocket("ws://localhost:8080/dashboard");

    ws.onmessage = (event) => {
      const data: GraphData = JSON.parse(event.data);
      console.log(data);

      setElements((prevElements) => {
        // Convert the existing elements into a map for quick lookup
        const elementMap = new Map(prevElements.map((el) => [el.data.id, el]));

        // Update nodes with new clusterId and other data
        data.nodes.forEach((newNode) => {
          if (elementMap.has(newNode.data.id)) {
            const existingNode = elementMap.get(newNode.data.id);
            if (existingNode) {
              existingNode.data.clusterId = newNode.data.clusterId; // Update clusterId
              existingNode.data.label = newNode.data.label; // Update label
            }
          } else {
            // If node is new, add it to the map
            elementMap.set(newNode.data.id, {
              data: newNode.data,
            });
          }
        });

        // Update edges
        data.edges.forEach((newEdge) => {
          if (
            !elementMap.has(`${newEdge.data.source}-${newEdge.data.target}`)
          ) {
            elementMap.set(`${newEdge.data.source}-${newEdge.data.target}`, {
              data: newEdge.data,
            });
          }
        });

        // Trigger Cytoscape style update after updating elements
        if (cyRef.current) {
          data.nodes.forEach((node) => {
            const ele = cyRef.current!.getElementById(node.data.id);
            if (ele) {
              ele.data("clusterId", node.data.clusterId); // Update clusterId
            }
          });
          cyRef.current.style().update(); // Reapply styles
        }

        // Return the updated elements array
        return Array.from(elementMap.values());
      });
    };

    return () => {
      ws.close();
    };
  }, []);

  const clusterColors = {
    "54f34e52-b466-49f8-b525-230cd107148b": "#2196f3", // New York
    "36f06b8b-cd19-4c72-a9a6-2f37baf4a422": "#bf2614", // Berlin
    "66472086-5a7b-461e-883e-2d4d4763e34d": "#4caf50", // Singapore
  };

  //   // Create cluster labels using the same IDs as clusterColors
  //   const clusterLabels = Object.entries(clusterColors).map(([clusterId, color]) => ({
  //     data: { id: clusterId, label: clusterId },
  //     classes: "clusterLabel",
  //   }));

  const stylesheet: cytoscape.Stylesheet[] = [
    {
      selector: "node",
      style: {
        "background-color": (ele: cytoscape.NodeSingular) => {
          const clusterId = ele.data("clusterId") as string;
          return (
            clusterColors[clusterId as keyof typeof clusterColors] || "#9e9e9e"
          );
        },
        label: "data(label)",
        color: "#ffffff",
        "text-outline-width": 2,
        "text-outline-color": (ele: cytoscape.NodeSingular) => {
          const clusterId = ele.data("clusterId") as string;
          return (
            clusterColors[clusterId as keyof typeof clusterColors] || "#9e9e9e"
          );
        },
        "text-halign": "center", // Use "center" directly
        "text-valign": "center", // Use "center" directly
      },
    },
    {
      selector: "edge",
      style: {
        // "curve-style": "bezier", // Adds curvature to edges for better arrow placement
        "control-point-step-size": 20, // Increases spacing between overlapping edges
        "line-color": "#bdbdbd",
        "target-arrow-color": "#bdbdbd",
        "target-arrow-shape": "triangle",
        "source-arrow-shape": "triangle", // Arrow at the source end
        "arrow-scale": 2, // Scale of the arrows
        width: "mapData(weight, 0, 100, 1, 6)", // Edge width based on weight
        // width: 2,
        label: "data(weight)",
        "font-size": "12px",
        color: "#ff0000",
        "text-margin-y": -10,
        "text-rotation": "autorotate",
      },
    },
    {
      selector: ".clusterLabel",
      style: {
        "background-color": (ele: cytoscape.NodeSingular) => {
          const clusterId = ele.data("id") as string;
          return (
            clusterColors[clusterId as keyof typeof clusterColors] || "#9e9e9e"
          );
        },
        label: "data(label)",
        color: "#ffffff",
        "text-outline-width": 2,
        "text-outline-color": (ele: cytoscape.NodeSingular) => {
          const clusterId = ele.data("id") as string;
          return (
            clusterColors[clusterId as keyof typeof clusterColors] || "#9e9e9e"
          );
        },
        "text-valign": "center", // Use "center" directly
        "text-halign": "center", // Use "center" directly
        shape: "round-rectangle",
        width: 100,
        height: 40,
      },
    },
  ];

  // Prepare elements with clusterLabels
  const clusterLabels = Object.keys(clusterColors).map((clusterId) => ({
    data: { id: clusterId, label: clusterId }, // Use `id` from `clusterColors` as `id`
    classes: "clusterLabel",
  }));

  //   const clusterLabels = [
  //     { data: { id: "NewYorkCluster", label: "New York", isClusterLabel: true } },
  //     { data: { id: "BerlinCluster", label: "Berlin", isClusterLabel: true } },
  //     { data: { id: "SingaporeCluster", label: "Singapore", isClusterLabel: true } },
  //   ];

  const fcoseLayout = {
    name: "fcose",
    incremental: true, // Preserve previous positions for smooth transitions
    // randomize: true,
    fit: true,
    padding: 30,
    nodeRepulsion: 4500,
    nodeSeparation: 500, // Adequate spacing between microservices within a cluster
    nestingFactor: 1.2, // Adjust cluster size to fit microservices
    gravityCompound: 2, // Strong grouping for microservices within the same cluster
    gravityRangeCompound: 3, // Balance cluster tightness with overall layout
    // idealEdgeLength: 100,
    // idealEdgeLength: (edge) => 2000 / edge.data("weight"), // Adjust edge lengths based on affinity weight
    idealEdgeLength: 100,
    edgeElasticity: 0.45,
    gravity: 0.25,
    clustering: {
      clusterPadding: 50, // Padding between clusters
    },
  };

  return (
    <div style={{ border: "1px solid red", width: "100%", height: "100%" }}>
      <CytoscapeComponent
        elements={elements}
        layout={fcoseLayout}
        style={{ width: "100%", height: "600px" }}
        stylesheet={stylesheet}
        cy={(cy) => (cyRef.current = cy)} // Assign Cytoscape instance to ref
      />
    </div>
  );
};

export default Graph;
