"use client";

import React, { useEffect, useRef } from "react";
import CytoscapeComponent from "react-cytoscapejs";
import cytoscape from "cytoscape";
import fcose from "cytoscape-fcose";

// Register the fcose layout
cytoscape.use(fcose);


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

interface GraphProps {
  data: GraphData | null; // Graph data passed from the parent component
}

const Graph: React.FC<GraphProps> = ({ data }) => {
  const cyRef = useRef<cytoscape.Core | null>(null); // Reference to Cytoscape instance

  useEffect(() => {
    if (data && cyRef.current) {
      // Update Cytoscape elements when new data is received
      cyRef.current.json({
        elements: {
          nodes: data.nodes,
          edges: data.edges,
        },
      });

      // Reapply styles to ensure the graph is updated
      cyRef.current.style().update();
    }
  }, [data]);

  const clusterColors = {
    "54f34e52-b466-49f8-b525-230cd107148b": "#2196f3", // New York
    "36f06b8b-cd19-4c72-a9a6-2f37baf4a422": "#bf2614", // Berlin
    "66472086-5a7b-461e-883e-2d4d4763e34d": "#4caf50", // Singapore
    "c5f8a0e9-2fbc-41b4-90b8-a83f6a1c5b68": "#6666FF", // Los Angeles
    "45d20af3-96c5-47db-8149-f92ae47df3e3": "#B0B0B0", // London
    "7b9a0d5e-8d13-4f8a-89e2-51b4733df14c": "#FF9933", // Sydney
  };

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
        "text-halign": "center",
        "text-valign": "center",
      },
    },
    {
      selector: "edge",
      style: {
        "line-color": "#bdbdbd",
        "target-arrow-color": "#bdbdbd",
        "target-arrow-shape": "triangle",
        "source-arrow-shape": "triangle",
        "arrow-scale": 2,
        width: "mapData(weight, 0, 100, 1, 6)",
        label: "data(weight)",
        "font-size": "12px",
        color: "#ff0000",
        "text-margin-y": -10,
        "text-rotation": "autorotate",
      },
    },
  ];

  const fcoseLayout = {
    name: "fcose",
    incremental: true,
    fit: true,
    padding: 30,
    nodeRepulsion: 4500,
    nodeSeparation: 500,
    nestingFactor: 1.2,
    gravityCompound: 2,
    gravityRangeCompound: 3,
    idealEdgeLength: 100,
    edgeElasticity: 0.45,
    gravity: 0.25,
  };

  return (
    <div style={{ width: "100%", height: "100%" }}>
      <CytoscapeComponent
        elements={data ? [...data.nodes, ...data.edges] : []}
        layout={fcoseLayout}
        style={{ width: "100%", height: "600px" }}
        stylesheet={stylesheet}
        cy={(cy) => (cyRef.current = cy)} // Assign Cytoscape instance to ref
      />
    </div>
  );
};

export default Graph;
