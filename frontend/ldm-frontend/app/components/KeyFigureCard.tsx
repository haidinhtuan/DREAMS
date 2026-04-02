import React from "react";
import { Card, CardContent, Typography } from "@mui/material";

interface KeyFigureCardProps {
  label: string;
  value: string | number;
}

const KeyFigureCard: React.FC<KeyFigureCardProps> = ({ label, value }) => (
  <Card>
    <CardContent>
      <Typography variant="subtitle2" color="textSecondary">
        {label}
      </Typography>
      <Typography variant="h6">{value}</Typography>
    </CardContent>
  </Card>
);

export default KeyFigureCard;
