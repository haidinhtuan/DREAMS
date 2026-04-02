import React from "react";
import Graph from "../components/Graph";

const GraphPage: React.FC = () => {
  // Refactor/Improve:
  // -Dashboard Title Header Section
  // -4 Cards: Number of applied Migrations;Last Migrated Microservice;Migration Duration;Number of Leader Changes
  // -1 Card (Whole width in a new row): Microservices Graph
  return (
    <div>
      <h1>Microservices Graph</h1>
      <Graph data={null} />
    </div>
  );
};

export default GraphPage;
