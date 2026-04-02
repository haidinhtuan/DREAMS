"use client";

import React, { useEffect, useState, useMemo } from "react";
import {
  Box,
  Grid,
  Card,
  CardContent,
  Typography,
  Button,
} from "@mui/material";
import Graph from "../components/Graph";
import KeyFigureCard from "../components/KeyFigureCard";
import {
  MaterialReactTable,
  useMaterialReactTable,
  type MRT_ColumnDef,
  type MRT_Row,
} from "material-react-table";
import FileDownloadIcon from "@mui/icons-material/FileDownload";
import { mkConfig, generateCsv, download } from "export-to-csv";

const WS_HOST = process.env.NEXT_PUBLIC_WS_HOST || "localhost";
const WS_PORTS = (process.env.NEXT_PUBLIC_WS_PORTS || "8080,8081,8082,8083,8084,8085").split(",");

type KeyFigures = {
  appliedMigrations: number;
  lastMigratedMicroservice: string;
  leaderChanges: number;
  currentLeader: string;
  measurementDuration: string; // New property for measurement duration
  createdBy: string; // New property for measurement creator
};

type MeasurementData = {
  processId?: string;
  processName?: string;
  result?: string;
  startTime?: string;
  endTime?: string;
  durationInMs?: number;
  createdBy?: string;
  timestamp?: string;
  [key: string]: string | number | undefined;
};

const csvConfig = mkConfig({
  fieldSeparator: ",",
  decimalSeparator: ".",
  useKeysAsHeaders: true,
});

const Dashboard: React.FC = () => {
  const [keyFigures, setKeyFigures] = useState<KeyFigures>({
    appliedMigrations: 0,
    lastMigratedMicroservice: "N/A",
    leaderChanges: 0,
    currentLeader: "N/A",
    measurementDuration: "N/A", // Initial value
    createdBy: "N/A", // Initial value
  });

  const [graphData, setGraphData] = useState<{
    nodes: { data: { id: string; label: string; clusterId: string; location: string } }[];
    edges: { data: { source: string; target: string; weight: number } }[];
  } | null>(null);
  const [measurementData, setMeasurementData] = useState<MeasurementData[]>([]);
  const handleWebSocketMessage = (event: MessageEvent) => {
    let data;
    try {
      data = JSON.parse(event.data);
    } catch (e) {
      console.error("Failed to parse WebSocket message:", e);
      return;
    }

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
          setGraphData((prev) =>
            prev
              ? {
                  ...prev,
                  nodes: [...prev.nodes, ...data.value.nodes],
                  edges: [...prev.edges, ...data.value.edges],
                }
              : data.value
          );
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
          break;
        default:
          console.warn("Unhandled WebSocket message:", data);
      }
    }
  };

  useEffect(() => {
    const connections: WebSocket[] = [];
    const reconnectDelays: number[] = WS_PORTS.map(() => 1000);
    const maxDelay = 30000;

    const connect = (index: number) => {
      const port = WS_PORTS[index].trim();
      const socket = new WebSocket(`ws://${WS_HOST}:${port}/dashboard`);

      socket.onopen = () => {
        console.log(`WebSocket connected: ${socket.url}`);
        reconnectDelays[index] = 1000; // reset backoff on success
      };
      socket.onmessage = handleWebSocketMessage;
      socket.onerror = (error) =>
        console.error(`WebSocket error on ${socket.url}:`, error);
      socket.onclose = () => {
        console.log(`WebSocket closed: ${socket.url}, reconnecting in ${reconnectDelays[index]}ms`);
        setTimeout(() => {
          reconnectDelays[index] = Math.min(reconnectDelays[index] * 2, maxDelay);
          connect(index);
        }, reconnectDelays[index]);
      };

      connections[index] = socket;
    };

    WS_PORTS.forEach((_, index) => connect(index));

    return () => {
      connections.forEach((socket) => socket?.close());
    };
  }, []);

  // Memoized columns definition
  const columns = useMemo<MRT_ColumnDef<MeasurementData>[]>(
    () => [
      {
        accessorKey: "processId",
        header: "Process ID",
        size: 150,
      },
      {
        accessorKey: "processName",
        header: "Process Name",
        size: 200,
      },
      {
        accessorKey: "result",
        header: "Result",
        size: 150,
      },
      {
        accessorKey: "startTime",
        header: "Start Time",
        size: 200,
      },
      {
        accessorKey: "endTime",
        header: "End Time",
        size: 200,
      },
      {
        accessorKey: "durationInMs",
        header: "Duration (ms)",
        size: 150,
      },
      {
        accessorKey: "createdBy",
        header: "Created By",
        size: 200,
      },
    ],
    [] // Ensures memoization
  );

  const handleExportRows = (rows: MRT_Row<MeasurementData>[]) => {
    const rowData = rows.map((row) => row.original);
    const csv = generateCsv(csvConfig)(rowData);
    download(csvConfig)(csv);
  };

  const handleExportData = () => {
    const csv = generateCsv(csvConfig)(measurementData);
    download(csvConfig)(csv);
  };

  const table = useMaterialReactTable({
    columns,
    data: measurementData, //data must be memoized or stable (useState, useMemo, defined outside of this component, etc.)
    renderTopToolbarCustomActions: ({ table }) => (
      <Box
        sx={{
          display: "flex",
          gap: "16px",
          padding: "8px",
          flexWrap: "wrap",
        }}
      >
        <Button
          //export all data that is currently in the table (ignore pagination, sorting, filtering, etc.)
          onClick={handleExportData}
          startIcon={<FileDownloadIcon />}
        >
          Export All Data
        </Button>
        <Button
          disabled={table.getPrePaginationRowModel().rows.length === 0}
          //export all rows, including from the next page, (still respects filtering and sorting)
          onClick={() =>
            handleExportRows(table.getPrePaginationRowModel().rows)
          }
          startIcon={<FileDownloadIcon />}
        >
          Export All Rows
        </Button>
        <Button
          disabled={table.getRowModel().rows.length === 0}
          //export all rows as seen on the screen (respects pagination, sorting, filtering, etc.)
          onClick={() => handleExportRows(table.getRowModel().rows)}
          startIcon={<FileDownloadIcon />}
        >
          Export Page Rows
        </Button>
        <Button
          disabled={
            !table.getIsSomeRowsSelected() && !table.getIsAllRowsSelected()
          }
          //only export selected rows
          onClick={() => handleExportRows(table.getSelectedRowModel().rows)}
          startIcon={<FileDownloadIcon />}
        >
          Export Selected Rows
        </Button>
      </Box>
    ),
  });

  return (
    <Box sx={{ padding: 4 }}>
      <Typography variant="h4" gutterBottom>
        Dashboard: Local Domain Managers
      </Typography>

      {/* Key Figures Section */}
      <Grid container spacing={3}>
        {Object.entries(keyFigures).map(([label, value], index) => (
          <Grid item xs={12} md={4} key={index}>
            <KeyFigureCard label={label} value={value} />
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

      {/* Measurement Data Table */}
      <Box>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Measurement Data
            </Typography>
            <MaterialReactTable table={table} />;
          </CardContent>
        </Card>
      </Box>
    </Box>
  );
};

export default Dashboard;
