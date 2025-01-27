"use client";

import React, { useEffect, useState } from "react";
import { Box, Grid, Card, CardContent, Typography } from "@mui/material";
import Graph from "../components/Graph";

// TypeScript type for key figures
type KeyFigures = {
  appliedMigrations: number;
  lastMigratedMicroservice: string;
  migrationDuration: string;
  leaderChanges: number;
  currentLeader: string; // Added property for current leader
};

type MessageTypes =
  | "MIGRATION_APPLIED"
  | "LAST_MIGRATED_MICROSERVICE"
  | "QOS_TIME_DURATION"
  | "LEADER_CHANGED"
  | "GRAPH_DATA";

const Dashboard: React.FC = () => {
  const [keyFigures, setKeyFigures] = useState<KeyFigures>({
    appliedMigrations: 0,
    lastMigratedMicroservice: "N/A",
    migrationDuration: "0s",
    leaderChanges: 0,
    currentLeader: "N/A", // Initial value
  });

  const [graphData, setGraphData] = useState<any>(null); // Update with the appropriate type for your graph data

  useEffect(() => {
    const socket = new WebSocket("ws://localhost:8080/dashboard");

    socket.onopen = () => {
      console.log("WebSocket connection established");
    };

    socket.onmessage = (event) => {
      const data = JSON.parse(event.data);
      console.log("Received data on websocket: ", data);

      if (data.type) {
        const messageType: MessageTypes = data.type;

        switch (messageType) {
          case "MIGRATION_APPLIED":
            setKeyFigures((prev) => ({
              ...prev,
              lastMigratedMicroservice: data.value.lastMigratedMicroservice,
              appliedMigrations: data.value.migrationsAppliedCount,
            }));
            break;
          case "LAST_MIGRATED_MICROSERVICE":
            setKeyFigures((prev) => ({
              ...prev,
              lastMigratedMicroservice: data.value,
            }));
            break;
          case "QOS_TIME_DURATION":
            setKeyFigures((prev) => ({
              ...prev,
              migrationDuration: data.value,
            }));
            break;
          case "LEADER_CHANGED":
            setKeyFigures((prev) => ({
              ...prev,
              leaderChanges: data.value.LEADER_CHANGE_COUNT,
              currentLeader: data.value.NEW_LEADER, // Update current leader
            }));
            break;
          case "GRAPH_DATA":
            setGraphData(data.value);
            break;
          default:
            console.warn("Unknown message type: ", messageType);
        }
      }
    };

    socket.onerror = (error) => {
      console.error("WebSocket error:", error);
    };

    socket.onclose = () => {
      console.log("WebSocket connection closed");
    };

    return () => {
      socket.close();
    };
  }, []);

  if (!keyFigures || !graphData) {
    return (
      <Box sx={{ padding: 4 }}>
        <Typography variant="h4" gutterBottom>
          Dashboard: Local Domain Managers
        </Typography>
        <Typography>Loading...</Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ padding: 4 }}>
      {/* Dashboard Title Header Section */}
      <Typography variant="h4" gutterBottom>
        Dashboard: Local Domain Managers
      </Typography>

      {/* Key Figures Section */}
      <Grid container spacing={3}>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography variant="h6">Number of Applied Migrations</Typography>
              <Typography variant="h4" color="primary">
                {keyFigures.appliedMigrations}
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography variant="h6">Last Migrated Microservice</Typography>
              <Typography variant="h4" color="primary">
                {keyFigures.lastMigratedMicroservice}
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography variant="h6">Last Migration Duration</Typography>
              <Typography variant="h4" color="primary">
                {keyFigures.migrationDuration}
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography variant="h6">Number of Leader Changes</Typography>
              <Typography variant="h4" color="primary">
                {keyFigures.leaderChanges}
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography variant="h6">Current Leader</Typography>
              <Typography variant="h4" color="primary">
                {keyFigures.currentLeader}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Microservices Graph Section */}
      <Box sx={{ marginTop: 4 }}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Global State
            </Typography>
            <Graph data={graphData} />
          </CardContent>
        </Card>
      </Box>
    </Box>
  );
};

export default Dashboard;
