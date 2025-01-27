"use client";

import React, { useEffect, useState } from "react";
import {
  Box,
  Grid,
  Card,
  CardContent,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
} from "@mui/material";
import Graph from "../components/Graph";

type KeyFigures = {
  appliedMigrations: number;
  lastMigratedMicroservice: string;
  leaderChanges: number;
  currentLeader: string;
  measurementDuration: string; // New property for measurement duration
  createdBy: string; // New property for measurement creator
};

type MeasurementData = {
  [key: string]: any;
};

const Dashboard: React.FC = () => {
  const [keyFigures, setKeyFigures] = useState<KeyFigures>({
    appliedMigrations: 0,
    lastMigratedMicroservice: "N/A",
    leaderChanges: 0,
    currentLeader: "N/A",
    measurementDuration: "N/A", // Initial value
    createdBy: "N/A", // Initial value
  });

  const [graphData, setGraphData] = useState<any>(null);
  const [measurementData, setMeasurementData] = useState<MeasurementData[]>([]);
  const [measurementKeys, setMeasurementKeys] = useState<string[]>([]);

  useEffect(() => {
    const socket = new WebSocket("ws://localhost:8080/dashboard");

    socket.onopen = () => console.log("WebSocket connection established");

    socket.onmessage = (event) => {
      const data = JSON.parse(event.data);

      if (data.type) {
        switch (data.type) {
          case "MIGRATION_APPLIED":
            setKeyFigures((prev) => ({
              ...prev,
              appliedMigrations: data.value.migrationsAppliedCount,
              lastMigratedMicroservice: data.value.lastMigratedMicroservice,
            }));
            break;
          case "LEADER_CHANGED":
            setKeyFigures((prev) => ({
              ...prev,
              leaderChanges: data.value.LEADER_CHANGE_COUNT,
              currentLeader: data.value.NEW_LEADER,
            }));
            break;
          case "GRAPH_DATA":
            console.log("Graph Data Received:", data.value);
            setGraphData(data.value);
            break;
          case "MEASUREMENT_DATA":
            console.log("Measurement Data Received:", data.value);
            const enrichedData = {
              ...data.value,
              timestamp: new Date().toLocaleString(),
            };
            setKeyFigures((prev) => ({
              ...prev,
              measurementDuration: `${data.value.durationInMs} ms`,
              createdBy: data.value.createdBy,
            }));
            setMeasurementData((prev) => [...prev, enrichedData]);
            setMeasurementKeys((prevKeys) => {
              const newKeys = Object.keys(enrichedData);
              return Array.from(new Set([...prevKeys, ...newKeys]));
            });
            break;
          default:
            console.warn("Unhandled WebSocket message:", data);
        }
      }
    };

    socket.onclose = () => console.log("WebSocket connection closed");
    return () => socket.close();
  }, []);

  return (
    <Box sx={{ padding: 4 }}>
      <Typography variant="h4" gutterBottom>
        Dashboard: Local Domain Managers
      </Typography>

      {/* Key Figures Section */}
      <Grid container spacing={3}>
        {Object.entries(keyFigures).map(([label, value], index) => (
          <Grid item xs={12} md={4} key={index}>
            <Card>
              <CardContent>
                <Typography variant="h6">{label}</Typography>
                <Typography>{value}</Typography>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      {/* Microservices Graph Section */}
      <Box sx={{ marginTop: 4, minHeight: "400px" }}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Global State
            </Typography>
            {graphData ? (
              <Graph data={graphData} />
            ) : (
              <Typography color="textSecondary">
                No graph data available.
              </Typography>
            )}
          </CardContent>
        </Card>
      </Box>

      {/* Measurement Data Table Section */}
      <Box sx={{ marginTop: 4 }}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Measurement Data
            </Typography>
            {measurementData.length > 0 ? (
              <TableContainer component={Paper}>
                <Table>
                  <TableHead>
                    <TableRow>
                      {measurementKeys.map((key) => (
                        <TableCell key={key}>{key}</TableCell>
                      ))}
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {measurementData.map((row, index) => (
                      <TableRow key={index}>
                        {measurementKeys.map((key) => (
                          <TableCell key={key}>{row[key]}</TableCell>
                        ))}
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            ) : (
              <Typography color="textSecondary">
                No measurement data available.
              </Typography>
            )}
          </CardContent>
        </Card>
      </Box>
    </Box>
  );
};

export default Dashboard;
