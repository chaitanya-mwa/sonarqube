/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import * as React from 'react';
import { inRange } from 'lodash';
import styled, { Theme } from '../styled';

interface Props {
  className?: string;
  muted?: boolean;
  small?: boolean;
  value: number;
}

function SizeRating({ className, value }: Props) {
  let letter;
  if (inRange(value, 0, 1000)) {
    letter = 'XS';
  } else if (inRange(value, 1000, 10000)) {
    letter = 'S';
  } else if (inRange(value, 10000, 100000)) {
    letter = 'M';
  } else if (inRange(value, 100000, 500000)) {
    letter = 'L';
  } else if (value >= 500000) {
    letter = 'XL';
  }

  return <div className={className}>{letter}</div>;
}

const size = (props: Props & Theme) =>
  props.small ? props.theme.smallControlHeight : props.theme.controlHeight;

export default styled(SizeRating)`
  display: inline-block;
  vertical-align: top;
  width: ${size};
  height: ${size};
  line-height: ${size};
  margin-top: ${props => (props.small ? '-1px' : 0)};
  margin-bottom: ${props => (props.small ? '-1px' : 0)};
  border-radius: ${size};
  background-color: ${props => (props.muted ? '#bdbdbd' : props.theme.blue)};
  color: #fff;
  font-size: ${props => (props.small ? props.theme.smallestFontSize : props.theme.smallFontSize)};
  text-align: center;
  text-shadow: 0 0 1px rgba(0, 0, 0, 0.35);
`;
